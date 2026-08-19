package dev.mintychochip.tradingpost;

import dev.mintychochip.mint.api.service.MintClientLease;
import dev.mintychochip.mint.api.service.MintClientReceiver;
import dev.mintychochip.tradingpost.command.TradingPostCommands;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.db.Database;
import dev.mintychochip.tradingpost.db.MigrationRunner;
import dev.mintychochip.tradingpost.db.SqlDialect;
import dev.mintychochip.tradingpost.lifecycle.AsyncExecutor;
import dev.mintychochip.tradingpost.lifecycle.PluginState;
import dev.mintychochip.tradingpost.mailbox.MailboxService;
import dev.mintychochip.tradingpost.market.OrderService;
import dev.mintychochip.tradingpost.mint.MintGateway;
import dev.mintychochip.tradingpost.post.TradingPostListener;
import dev.mintychochip.tradingpost.post.TradingPostRegistry;
import dev.mintychochip.tradingpost.settlement.ExpiryWorker;
import dev.mintychochip.tradingpost.settlement.ReconciliationWorker;
import dev.mintychochip.tradingpost.settlement.SettlementRecoveryWorker;
import dev.mintychochip.tradingpost.settlement.SettlementService;
import dev.mintychochip.tradingpost.ui.TradingPostMenu;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradingPostPlugin extends JavaPlugin implements MintClientReceiver {
  private volatile PluginState state = PluginState.STARTING;
  private TradingPostConfig configuration;
  private AsyncExecutor executor;
  private volatile Database database;
  private MintGateway mint;
  private TradingPostRegistry registry;
  private TradingPostMenu menu;
  private SettlementService settlementService;
  private OrderService orderService;
  private MailboxService mailboxService;
  private final AtomicBoolean persistenceReady = new AtomicBoolean();
  private final AtomicBoolean initializing = new AtomicBoolean();
  private volatile Throwable startupFailure;
  private int readinessTask = -1;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    try {
      configuration = TradingPostConfig.load(getConfig());
      executor = new AsyncExecutor(32);
      mint = new MintGateway(configuration);
      Bukkit.getServicesManager()
          .register(MintClientReceiver.class, this, this, ServicePriority.Normal);
    } catch (RuntimeException failure) {
      failEnable(failure);
      return;
    }
    executor
        .submit(
            () -> {
              Database created = new Database(configuration);
              try {
                MigrationRunner.migrate(created, configuration);
                database = created;
                return null;
              } catch (Throwable failure) {
                created.close();
                throw failure;
              }
            })
        .whenComplete(
            (ignored, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        this,
                        () -> {
                          if (failure != null) {
                            startupFailure = failure;
                            getLogger()
                                .severe(
                                    "TradingPost database startup failed: " + failure.getMessage());
                          } else {
                            persistenceReady.set(true);
                          }
                        }));
    readinessTask = Bukkit.getScheduler().runTaskTimer(this, this::tryReady, 1L, 20L).getTaskId();
    getLogger().info("TradingPost is STARTING while PostgreSQL and Mint become ready");
  }

  @Override
  public void bindMintClient(MintClientLease lease) {
    if (mint == null) {
      throw new IllegalStateException("TradingPost is not initialized");
    }
    mint.bindMintClient(lease);
  }

  private void tryReady() {
    if (state != PluginState.STARTING || !initializing.compareAndSet(false, true)) {
      return;
    }
    if (startupFailure != null) {
      initializing.set(false);
      failEnable(startupFailure);
      return;
    }
    if (!persistenceReady.get() || !mint.ready()) {
      initializing.set(false);
      return;
    }
    mint.validateCurrency()
        .thenCombine(
            mint.ensureSystemAccounts(),
            (currencyExists, accountsReady) -> currencyExists && accountsReady)
        .whenComplete(
            (valid, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        this,
                        () -> {
                          initializing.set(false);
                          if (state != PluginState.STARTING) {
                            return;
                          }
                          if (failure != null || !Boolean.TRUE.equals(valid)) {
                            failEnable(
                                failure == null
                                    ? new IllegalStateException(
                                        "Mint currency or TradingPost account validation failed")
                                    : failure);
                            return;
                          }
                          state = PluginState.READY;
                          if (readinessTask >= 0) {
                            Bukkit.getScheduler().cancelTask(readinessTask);
                            readinessTask = -1;
                          }
                          initializeInterface();
                          getLogger().info("TradingPost is READY");
                        }));
  }

  private void initializeInterface() {
    settlementService = new SettlementService(database, configuration, mint, executor);
    orderService = new OrderService(this, database, configuration, settlementService, executor);
    var dialect = SqlDialect.from(configuration);
    mailboxService = new MailboxService(this, database, dialect, executor);
    registry = new TradingPostRegistry(database, dialect, executor, this);
    SettlementRecoveryWorker recovery =
        new SettlementRecoveryWorker(
            database,
            dialect,
            getServer().getName() + "-" + java.util.UUID.randomUUID(),
            settlementService,
            executor);
    ExpiryWorker expiry =
        new ExpiryWorker(
            database,
            dialect,
            settlementService,
            executor,
            configuration.maxSellOrders() + configuration.maxBuyOrders());
    ReconciliationWorker reconciliation =
        new ReconciliationWorker(database, dialect, mint, executor);
    menu =
        new TradingPostMenu(this, database, configuration, orderService, mailboxService, executor);
    registry
        .load()
        .whenComplete(
            (ignored, failure) ->
                Bukkit.getScheduler()
                    .runTask(
                        this,
                        () -> {
                          if (failure != null) {
                            failEnable(failure);
                            return;
                          }
                          Bukkit.getPluginManager().registerEvents(menu.asListener(), this);
                          Bukkit.getPluginManager()
                              .registerEvents(
                                  new TradingPostListener(
                                      registry,
                                      () -> state == PluginState.READY,
                                      (player, context) -> menu.open(player, context.marketName())),
                                  this);
                          new TradingPostCommands(this, registry, menu, configuration, true)
                              .register();
                          Bukkit.getScheduler()
                              .runTaskTimer(
                                  this,
                                  () ->
                                      recovery
                                          .runOnce()
                                          .exceptionally(
                                              failure1 -> {
                                                getLogger()
                                                    .warning(
                                                        "Settlement recovery failed: "
                                                            + failure1.getMessage());
                                                return 0;
                                              }),
                                  1L,
                                  ticks(configuration.recoverySweep()));
                          Bukkit.getScheduler()
                              .runTaskTimer(
                                  this,
                                  () ->
                                      expiry
                                          .runOnce()
                                          .exceptionally(
                                              failure1 -> {
                                                getLogger()
                                                    .warning(
                                                        "Expiry sweep failed: "
                                                            + failure1.getMessage());
                                                return 0;
                                              }),
                                  ticks(configuration.expirySweep()),
                                  ticks(configuration.expirySweep()));
                          Bukkit.getScheduler()
                              .runTaskTimer(
                                  this,
                                  () ->
                                      reconciliation
                                          .runOnce()
                                          .exceptionally(
                                              failure1 -> {
                                                getLogger()
                                                    .warning(
                                                        "Reconciliation failed: "
                                                            + failure1.getMessage());
                                                return null;
                                              }),
                                  ticks(configuration.reconciliation()),
                                  ticks(configuration.reconciliation()));
                        }));
  }

  private static long ticks(java.time.Duration duration) {
    return Math.max(1L, (duration.toMillis() + 49L) / 50L);
  }

  private void failEnable(Throwable failure) {
    getLogger().severe("TradingPost cannot start: " + failure.getMessage());
    state = PluginState.DEGRADED;
    getServer().getPluginManager().disablePlugin(this);
  }

  @Override
  public void onDisable() {
    state = PluginState.SHUTTING_DOWN;
    if (readinessTask >= 0) {
      Bukkit.getScheduler().cancelTask(readinessTask);
      readinessTask = -1;
    }
    Bukkit.getServicesManager().unregister(MintClientReceiver.class, this);
    if (mint != null) {
      mint.clearMintClient();
    }
    if (menu != null) {
      menu.shutdown();
    }
    Database closing = database;
    if (executor != null) {
      executor.shutdown(
          configuration == null ? java.time.Duration.ofSeconds(10) : configuration.shutdownGrace(),
          closing == null ? () -> {} : closing::close);
    } else if (closing != null) {
      Thread.startVirtualThread(closing::close);
    }
    state = PluginState.STOPPED;
  }

  public PluginState state() {
    return state;
  }

  public TradingPostConfig configuration() {
    return configuration;
  }

  public AsyncExecutor executor() {
    return executor;
  }

  public Database database() {
    return database;
  }

  public MintGateway mint() {
    return mint;
  }
}

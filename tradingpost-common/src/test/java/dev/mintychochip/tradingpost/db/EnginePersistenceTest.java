package dev.mintychochip.tradingpost.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.tradingpost.config.DatabaseEngine;
import dev.mintychochip.tradingpost.config.TestConfigs;
import dev.mintychochip.tradingpost.config.TradingPostConfig;
import dev.mintychochip.tradingpost.domain.BuyOrder;
import dev.mintychochip.tradingpost.domain.Market;
import dev.mintychochip.tradingpost.domain.OrderStatus;
import dev.mintychochip.tradingpost.domain.SellOrder;
import dev.mintychochip.tradingpost.domain.SellOrderMode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EnginePersistenceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withLogConsumer(frame -> {});

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4").withLogConsumer(frame -> {});

  @Container
  static final MariaDBContainer<?> MARIADB =
      new MariaDBContainer<>("mariadb:11").withLogConsumer(frame -> {});

  @ParameterizedTest
  @EnumSource(DatabaseEngine.class)
  void migrateThenRoundTripMarketOrders(DatabaseEngine engine) throws Exception {
    Path sqliteFile =
        engine == DatabaseEngine.SQLITE ? Files.createTempFile("tradingpost", ".db") : null;
    TradingPostConfig config = configFor(engine, sqliteFile);
    try (Database database = new Database(config)) {
      MigrationRunner.migrate(database, config);
      MarketRepository markets = new MarketRepository(config);
      OrderRepository orders = new OrderRepository(config);

      UUID seller = UUID.randomUUID();
      UUID buyer = UUID.randomUUID();
      UUID sellId = UUID.randomUUID();
      UUID buyId = UUID.randomUUID();
      Instant now = Instant.parse("2026-08-19T12:00:00Z");
      byte[] blob = new byte[] {1, 2, 3, 9};

      SellOrder sell =
          new SellOrder(
              sellId,
              "spawn",
              seller,
              "minecraft:diamond",
              blob,
              "fp-sell",
              4,
              4,
              new BigDecimal("8.50"),
              SellOrderMode.NORMAL,
              OrderStatus.ACTIVE,
              now.plusSeconds(3600),
              now);
      BuyOrder buy =
          new BuyOrder(
              buyId,
              "spawn",
              buyer,
              "minecraft:diamond",
              null,
              2,
              2,
              new BigDecimal("9.00"),
              new BigDecimal("18.00"),
              OrderStatus.OPEN,
              now.plusSeconds(3600),
              now);

      database.transaction(
          connection -> {
            markets.insert(connection, new Market("spawn", "Spawn", 100, 500));
            orders.insertSell(connection, sell);
            orders.insertBuy(connection, buy);
            return null;
          });

      database.transaction(
          connection -> {
            Market market = markets.find(connection, "spawn").orElseThrow();
            assertEquals("spawn", market.name());
            assertEquals(100, market.feeBps());
            SellOrder loadedSell = orders.findSell(connection, sellId, false).orElseThrow();
            assertEquals(sellId, loadedSell.id());
            assertEquals("minecraft:diamond", loadedSell.material());
            assertEquals(4, loadedSell.quantityRemaining());
            assertArrayEquals(blob, loadedSell.itemBlob());
            BuyOrder loadedBuy = orders.findBuy(connection, buyId, false).orElseThrow();
            assertEquals(buyId, loadedBuy.id());
            assertEquals(buyer, loadedBuy.buyer());
            assertEquals(0, new BigDecimal("18.00").compareTo(loadedBuy.escrowReserved()));
            assertTrue(MigrationRunner.tableExists(connection, SqlDialect.from(config), "fills"));
            assertFalse(
                MigrationRunner.tableExists(connection, SqlDialect.from(config), "mailbox_items"));
            return null;
          });
    } finally {
      if (sqliteFile != null) {
        Files.deleteIfExists(sqliteFile);
      }
    }
  }

  private TradingPostConfig configFor(DatabaseEngine engine, Path sqliteFile) {
    return switch (engine) {
      case POSTGRESQL ->
          TestConfigs.jdbc(
              engine,
              POSTGRES.getJdbcUrl(),
              POSTGRES.getUsername(),
              POSTGRES.getPassword(),
              "engine_pg");
      case MYSQL ->
          TestConfigs.jdbc(
              engine,
              MYSQL.getJdbcUrl(),
              MYSQL.getUsername(),
              MYSQL.getPassword(),
              MYSQL.getDatabaseName());
      case MARIADB ->
          TestConfigs.jdbc(
              engine,
              MARIADB.getJdbcUrl(),
              MARIADB.getUsername(),
              MARIADB.getPassword(),
              MARIADB.getDatabaseName());
      case SQLITE ->
          TestConfigs.jdbc(
              engine, "jdbc:sqlite:" + sqliteFile.toAbsolutePath(), "", "", "tradingpost");
    };
  }
}

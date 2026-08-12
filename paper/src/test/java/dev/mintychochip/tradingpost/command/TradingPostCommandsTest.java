package dev.mintychochip.tradingpost.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TradingPostCommandsTest {
  @Test
  void routesOnlyTheRenamedCommands() {
    assertEquals(TradingPostCommands.CommandRoute.PLAYER, TradingPostCommands.route("post"));
    assertEquals(TradingPostCommands.CommandRoute.PLAYER, TradingPostCommands.route("POST"));
    assertEquals(TradingPostCommands.CommandRoute.ADMIN, TradingPostCommands.route("postadmin"));
    assertEquals(TradingPostCommands.CommandRoute.UNKNOWN, TradingPostCommands.route("ah"));
    assertEquals(TradingPostCommands.CommandRoute.UNKNOWN, TradingPostCommands.route("ahadmin"));
    assertEquals(TradingPostCommands.CommandRoute.UNKNOWN, TradingPostCommands.route("other"));
  }

  @Test
  void descriptorExposesOnlyTheRenamedCommands() throws IOException {
    String descriptor;
    try (InputStream resource = getClass().getResourceAsStream("/paper-plugin.yml")) {
      descriptor = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(descriptor.contains("\n  post:\n"));
    assertTrue(descriptor.contains("\n  postadmin:\n"));
    assertFalse(descriptor.contains("\n  ah:\n"));
    assertFalse(descriptor.contains("\n  ahadmin:\n"));
  }
}

package dev.mintychochip.tradingpost;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommonClasspathIsolationTest {
  @Test
  void testClasspathDoesNotContainPaperOrBukkit() {
    ClassNotFoundException bukkit =
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.Bukkit"));
    ClassNotFoundException paper =
        assertThrows(
            ClassNotFoundException.class, () -> Class.forName("io.papermc.paper.ServerBuildInfo"));
    ClassNotFoundException paperApi =
        assertThrows(
            ClassNotFoundException.class,
            () -> Class.forName("io.papermc.paper.plugin.ApiVersion"));
    assertTrue(bukkit.getMessage().contains("org.bukkit"));
    assertTrue(paper.getMessage().contains("io.papermc") || paper.getMessage().contains("paper"));
    assertTrue(
        paperApi.getMessage().contains("io.papermc") || paperApi.getMessage().contains("paper"));
  }
}

package dev.mintychochip.tradingpost.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DatabaseEngineTest {
  @ParameterizedTest
  @CsvSource({"postgresql,POSTGRESQL", "mysql,MYSQL", "mariadb,MARIADB", "sqlite,SQLITE"})
  void parseAcceptsConfiguredEngineNames(String name, DatabaseEngine engine) {
    assertEquals(engine, DatabaseEngine.parse(name));
    assertEquals(name, engine.configName());
  }

  @Test
  void parseRejectsBlankAndUnknownEngines() {
    assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.parse(""));
    assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.parse("oracle"));
  }
}

package de.insulink.api.log;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Pins what every log line looks like: the file format stays plain text a
 * grep can read, while the console format wraps the line in the ANSI color
 * that belongs to its level and closes it again. Both keep the
 * "[time - LEVEL]: message" prefix the log files are parsed by.
 */
final class LogFormatTest {
  private static final String COLOR_RESET = "[0m";
  private static final String COLOR_RED = "\033[0;91m";
  private static final String COLOR_DARK_RED = "\033[0;31m";
  private static final String COLOR_CYAN = "\033[0;36m";

  private LogRecord recordAt(Level level, String message) {
    var record = new LogRecord(level, message);
    record.setMillis(0L);
    return record;
  }

  private String fileLine(Level level, String message) {
    return LogFormat.create(LogFormat.FormatType.FILE).format(recordAt(level, message));
  }

  private String consoleLine(Level level, String message) {
    return LogFormat.create(LogFormat.FormatType.CONSOLE).format(recordAt(level, message));
  }

  @Test
  void theFileFormatCarriesLevelAndMessageWithoutAnyEscapeCodes() {
    var line = fileLine(Level.INFO, "started");
    Assertions.assertTrue(line.contains(Level.INFO.getLocalizedName()), line);
    Assertions.assertTrue(line.endsWith("started\n"), line);
    Assertions.assertFalse(line.contains("\033"), line);
    Assertions.assertFalse(line.contains(COLOR_RESET), line);
  }

  /**
   * The level is printed under its localized name, so the expectation asks the
   * level for it rather than hard-coding the English one — otherwise this test
   * only passes on an English machine.
   */
  @Test
  void thePrefixKeepsTheBracketedTimeAndLevelShape() {
    var expected = "\\[\\d\\d:\\d\\d:\\d\\d - " + Level.INFO.getLocalizedName() + "]: started\n";
    Assertions.assertTrue(fileLine(Level.INFO, "started").matches(expected));
  }

  @Test
  void warningsAreRedAndSevereIsDarkRed() {
    Assertions.assertTrue(consoleLine(Level.WARNING, "careful").contains(COLOR_RED));
    Assertions.assertTrue(consoleLine(Level.SEVERE, "broken").contains(COLOR_DARK_RED));
  }

  @Test
  void fineIsCyanAndInfoStaysUncolored() {
    Assertions.assertTrue(consoleLine(Level.FINE, "detail").contains(COLOR_CYAN));
    var info = consoleLine(Level.INFO, "plain");
    Assertions.assertFalse(info.contains(COLOR_RED), info);
    Assertions.assertFalse(info.contains(COLOR_CYAN), info);
  }

  @Test
  void everyColoredConsoleLineIsClosedAgainSoTheNextOneStartsClean() {
    Assertions.assertTrue(consoleLine(Level.WARNING, "careful").endsWith(COLOR_RESET + "\n"));
  }

  @Test
  void theFormatTypeKnowsWhichOneItIs() {
    Assertions.assertTrue(LogFormat.FormatType.CONSOLE.isConsole());
    Assertions.assertFalse(LogFormat.FormatType.CONSOLE.isFile());
    Assertions.assertTrue(LogFormat.FormatType.FILE.isFile());
    Assertions.assertFalse(LogFormat.FormatType.FILE.isConsole());
  }
}

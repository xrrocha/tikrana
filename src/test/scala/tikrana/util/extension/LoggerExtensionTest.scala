package tikrana.util.extension

import LoggerExtensions.*
import LogManagerExtensions.*
import java.util.logging.{Level, LogManager, Logger}

class LogManagerExtensionTest extends munit.FunSuite:
  test("Loads configuration from classpath"):
      val logManager = LogManager.getLogManager()
      logManager.readConfiguration("logging.properties")
      assertEquals(logManager.getLogger("").getLevel, Level.FINE)

class LoggerExtensionTest extends munit.FunSuite:
  test("logt logs lazily"):
      var changed = false
      val logger = Logger.getLogger("test")
      logger.setLevel(Level.INFO)
      logger.logt(Level.FINEST, {changed = true; "test message"})
      assert(!changed)
      logger.logt(Level.SEVERE, {changed = true; "test message"})
      assert(changed)
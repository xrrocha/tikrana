package tikrana.util.extension

import tikrana.util.Resources.openResource
import tikrana.util.extension.CloseableExtensions.use

import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.LogManager

import scala.util.Try

object LoggerExtensions:
  extension (logger: Logger)
    def logSevere(msg: => String): Unit =
      logger.severe(() => msg)
    def logSevere(t: Throwable, msg: => String): Unit =
      logger.log(Level.SEVERE, t, () => msg)
    def logWarning(msg: => String): Unit =
      logger.warning(() => msg)
    def logWarning(t: Throwable, msg: => String): Unit =
      logger.log(Level.WARNING, t, () => msg)
    def logConfig(msg: => String): Unit =
      logger.config(() => msg)
    def logConfig(t: Throwable, msg: => String): Unit =
      logger.log(Level.CONFIG, t, () => msg)
    def logFine(msg: => String): Unit =
      logger.fine(() => msg)
    def logFine(t: Throwable, msg: => String): Unit =
      logger.log(Level.FINE, t, () => msg)
    def logFiner(msg: => String): Unit =
      logger.finer(() => msg)
    def logFiner(t: Throwable, msg: => String): Unit =
      logger.log(Level.FINER, t, () => msg)
    def logFinest(msg: => String): Unit =
      logger.finest(() => msg)
    def logFinest(t: Throwable, msg: => String): Unit =
      logger.log(Level.FINEST, t, () => msg)

object LogManagerExtensions:
  extension (logManager: LogManager)
    def readConfiguration(resourceName: String): Try[Unit] =
      for 
        is <- openResource(resourceName)
        _ <- is.use(logManager.readConfiguration)
      yield ()
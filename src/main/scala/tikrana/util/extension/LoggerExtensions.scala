package tikrana.util.extension

import tikrana.util.Resources.openResource
import tikrana.util.extension.CloseableExtensions.use

import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.LogManager

import scala.util.Try

object LoggerExtensions:
  extension (logger: Logger)
    def logl(level: Level, msg: => String, throwable: Throwable = null): Unit =
      logger.log(level, throwable, () => msg)

object LogManagerExtensions:
  extension (logManager: LogManager)
    def readConfiguration(resourceName: String): Try[Unit] =
      for 
        is <- openResource(resourceName)
        _ <- is.use(logManager.readConfiguration)
      yield ()
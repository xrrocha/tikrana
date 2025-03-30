package tikrana.util.extension

import java.util.logging.Level
import java.util.logging.Logger

object LoggerExtensions:
  extension (logger: Logger)
    def logt(level: Level, msg: => String, throwable: Throwable = null): Unit =
      logger.log(Level.FINE, throwable, () => msg)

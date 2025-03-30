package tikrana.util

import java.util.logging.{Level, Logger}

class Fault(
    template: => String,
    val cause: Throwable | Null = null
) extends RuntimeException(template, cause):

  lazy val message: String = template

  def logAsSevere(logger: Logger): Fault =
    log(Level.SEVERE, logger)

  def logAsWarning(logger: Logger): Fault =
    log(Level.WARNING, logger)

  private def log(level: Level, logger: Logger): Fault =
    if cause == null then logger.log(level, () => message)
    else logger.log(level, cause, () => message)
    this
end Fault

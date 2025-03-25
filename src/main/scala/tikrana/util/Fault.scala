package tikrana.util

import scala.util.*

object Fault:
  def catching[B](f: => B): Either[Throwable, B] =
    try
      Right(f)
    catch
      case (t: Throwable) => Left(t)

  extension [A, B](either: Either[A, B])
    def mapLeft[C](f: A => C): Either[C, B] =
      either match
        case Right(right) => Right[C, B](right)
        case Left(left)   => Left[C, B](f(left))

    def peek(f: B => Unit): Either[A, B] =
      either match
        case Right(right) => f(right)
        case _ =>
      either

    def peekLeft(f: A => Unit): Either[A, B] =
      either match
        case Left(left) => f(left)
        case _ =>
      either

class Fault(
    template: => String,
    val cause: Option[Fault | Throwable] = None
):
  lazy val message = template

  def this(template: => String, cause: Fault | Throwable) =
    this(template, Some(cause))

  import java.util.logging.*
  def logSevere()(using logger: Logger) = log(Level.SEVERE, logger)
  def logWarning()(using logger: Logger) = log(Level.WARNING, logger)
  def logInfo()(using logger: Logger) = log(Level.INFO, logger)
  def logConfig()(using logger: Logger) = log(Level.CONFIG, logger)
  def logFine()(using logger: Logger) = log(Level.FINE, logger)
  def logFiner()(using logger: Logger) = log(Level.FINER, logger)
  def logFinest()(using logger: Logger) = log(Level.FINEST, logger)
  def log(level: Level, logger: Logger): Fault =
    cause match
      case Some(throwable: Throwable) =>
        logger.log(level, throwable, () => message)
      case _ =>
        logger.log(level, () => message)
    this

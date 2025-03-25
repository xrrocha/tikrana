package tikrana.util

import scala.util.*

object Fault:
  enum LogOpt:
    case WITH_STACK_TRACE, WITH_NO_STACK_TRACE

  def catching[B](f: => B): Either[Throwable, B] =
    try Right(f)
    catch case (t: Throwable) => Left(t)

  extension [A, B](either: Either[A, B])
    def mapLeft[C](f: A => C): Either[C, B] =
      either match
        case Right(right) => Right[C, B](right)
        case Left(left)   => Left[C, B](f(left))

    def peek(f: B => Unit): Either[A, B] =
      either match
        case Right(right) => f(right)
        case _            =>
      either

    def peekLeft(f: A => Unit): Either[A, B] =
      either match
        case Left(left) => f(left)
        case _          =>
      either

class Fault(
    template: => String,
    val cause: Option[Fault | Throwable] = None,
    val data: Option[Any] = None
):
  lazy val message = template

  def this(template: => String, cause: Fault | Throwable) =
    this(template, Some(cause))

  import Fault.LogOpt
  import Fault.LogOpt.*
  import annotation.tailrec
  import java.util.logging.*
  import java.util.logging.Level.*

  def logSevere(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(SEVERE, option, logger)

  def logWarning(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(Level.WARNING, option, logger)

  def logInfo(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(INFO, option, logger)

  def logConfig(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(CONFIG, option, logger)

  def logFine(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(FINE, option, logger)

  def logFiner(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(FINER, option, logger)

  def logFinest(
      logger: Logger,
      option: LogOpt = WITH_NO_STACK_TRACE
  ) =
    log(FINEST, option, logger)

  def log(
      level: Level,
      option: LogOpt,
      logger: Logger
  ): Fault =

    cause match

      case Some(t: Throwable) =>
        val tMessage =
          val msg = t.getMessage
          if msg != null then msg
          else t.toString

        option match

          case WITH_STACK_TRACE =>
            logger.log(level, t, () => message)

          case WITH_NO_STACK_TRACE =>
            logger.log(level, () => s"$message: $tMessage")

      case _ =>
        logger.log(level, () => message)
    this

  def logRecursive(
      level: Level,
      option: LogOpt,
      logger: Logger
  ): Fault =
    doLogRecursive(level, option, logger)
    this

  @tailrec
  private def doLogRecursive(
      level: Level,
      option: LogOpt,
      logger: Logger
  ): Unit =
    log(level, option, logger)
    cause match
      case Some(fault: Fault) =>
        fault.doLogRecursive(level, option, logger)
      case _ =>

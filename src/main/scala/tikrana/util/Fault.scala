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

class Fault(
    val message: String,
    val cause: Option[Fault | Throwable] = None
):
  def this(message: String, cause: Fault | Throwable) =
    this(message, Some(cause))

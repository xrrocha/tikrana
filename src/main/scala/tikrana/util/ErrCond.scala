package tikrana.util

import java.io.Closeable
import scala.util.*

object ErrCond:
  extension [A, B](either: Either[A, B])
    def mapLeft[C](f: A => C): Either[C, B] =
      either match
        case Right(right) => Right[C, B](right)
        case Left(left)   => Left[C, B](f(left))

class ErrCond(
    val message: String,
    val cause: Option[ErrCond | Throwable] = None,
    val extraInfo: Seq[(String, String)] = Seq.empty
):
  def this(message: String, cause: ErrCond | Throwable) =
    this(message, Some(cause))
  def this(
      message: String,
      cause: ErrCond | Throwable,
      extraInfo: (String, String)*
  ) =
    this(message, Some(cause), extraInfo)
  def this(message: String, extraInfo: (String, String)*) =
    this(message, None, extraInfo)

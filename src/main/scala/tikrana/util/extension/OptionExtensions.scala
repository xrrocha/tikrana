package tikrana.util.extension

import scala.util.{Failure, Success, Try}

object OptionExtensions:
  extension [T](opt: Option[T])
    def toTry(f: => Exception): Try[T] =
      opt
        .map(Success(_))
        .getOrElse(Failure(f))

  extension [T](optTry: Option[Try[T]])
    def swap: Try[Option[T]] =
      optTry
        .map(_.map(Some(_)))
        .getOrElse(Success(None))

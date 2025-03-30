package tikrana.util.extension

import scala.util.{Failure, Success, Try}

object OptionExtensions:
  extension [T](o: Option[T])
    def toTry(f: => Exception): Try[T] =
      o.map(Success(_)).getOrElse(Failure(f))

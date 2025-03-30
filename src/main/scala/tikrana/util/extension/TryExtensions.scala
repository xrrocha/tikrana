package tikrana.util.extension

import scala.util.{Failure, Success, Try}

object TryExtensions:

  extension [T](t: Try[T])

    def peek(f: T => Unit): Try[T] =
      t.foreach(f)
      t

    def peekFailure(f: Throwable => Unit): Try[T] =
      t match
        case Failure(t) => f(t)
        case _          =>
      t

    def mapFailure(f: Throwable => Exception): Try[T] =
      t match
        case Failure(t) => Failure(f(t))
        case _          => t

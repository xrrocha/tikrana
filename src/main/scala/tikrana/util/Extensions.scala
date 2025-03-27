package tikrana.util

import java.io.Closeable
import java.io.InputStream
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using
import tikrana.util.Types.*

object Extensions:
  // Option
  extension [T](o: Option[T])
    def toTry(f: => Exception): Try[T] =
      o match
        case Some(t) => Success(t)
        case None    => Failure(f)
  end extension

  // Try
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
  end extension

  // Logger
  extension (logger: Logger)
    def logt(level: Level, msg: => String, throwable: Throwable = null): Unit =
      logger.log(Level.FINE, throwable, () => msg)
  end extension

  // Closeable
  extension [A <: Closeable](closeable: A)
    def use[B](f: A => B): Try[B] =
      Using(closeable)(f)
  end extension

  // InputStream
  extension (inputStream: InputStream)
    def readBytes(): Try[ByteArray] =
      Using(inputStream)(_.readAllBytes())
    def readText(): Try[String] =
      inputStream
        .readBytes()
        .map(String(_, "UTF-8"))
  end extension

  // URL
  extension (u: URL)
    def readText(): Try[String] =
      Try(u.openStream())
        .flatMap(_.readText())
    def readBytes(): Try[ByteArray] =
      Try(u.openStream())
        .flatMap(_.readBytes())
  end extension
end Extensions

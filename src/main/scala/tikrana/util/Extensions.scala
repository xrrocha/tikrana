package tikrana.util

import tikrana.util.Types.*

import java.io.{Closeable, InputStream}
import java.net.URL
import java.util.logging.{Level, Logger}
import scala.util.{Failure, Success, Try, Using}

object Extensions:
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

  // Throwable
  extension (throwable: Throwable)
    def errorMessage: String =
      if throwable.getMessage != null then throwable.getMessage
      else throwable.toString
  end extension

  // Logger
  extension (logger: Logger)
    def logt(level: Level, msg: => String, throwable: Throwable = null): Unit =
      logger.log(Level.FINE, throwable, () => msg)
  end extension

  // String
  extension (string: String)
    def extension: Option[Extension] =
      val pos = string.lastIndexOf('.')
      if pos < 0 then None
      else Some(string.substring(pos + 1))
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
    def readText(encoding: String = "UTF-8"): Try[String] =
      inputStream
        .readBytes()
        .map(String(_, encoding))
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

package tikrana.util

import tikrana.util.Extensions.*
import tikrana.util.Types.*

import java.io.{FileNotFoundException, InputStream}
import java.net.URL
import scala.util.{Failure, Success, Try}

object Resources:
  def readResourceText(
      filename: Filename,
      encoding: String = "UTF-8"
  ): Try[String] =
    readResource(filename)
      .map(String(_, encoding))

  def readResource(filename: Filename): Try[Array[Byte]] =
    withResourceStream(filename)(_.readAllBytes())
      .mapFailure(Fault(s"Error while reading resource '$filename'", _))

  def withResourceStream[A](filename: Filename)(f: InputStream => A): Try[A] =
    getResourceAsStream(filename)
      .toTry(FileNotFoundException(s"No such resource: '$filename'"))
      .flatMap(is => Try(f(is)))

  def openResource(filename: Filename): Try[InputStream] =
    getResourceAsStream(filename)
      .toTry(Fault(s"No such resource $filename"))

  def getResourceAsStream(
      filename: Filename,
      classLoader: => ClassLoader = Utils.ctxClassLoader
  ): Option[InputStream] =
    Option(classLoader.getResourceAsStream(filename))

  def tryGetResource(
      filename: Filename,
      classLoader: => ClassLoader = Utils.ctxClassLoader
  ): Try[URL] =
    getResource(filename, classLoader)
      .map(Success(_))
      .getOrElse:
        Failure:
          FileNotFoundException(s"No such resource: $filename")

  def getResource(
      filename: Filename,
      classLoader: => ClassLoader = Utils.ctxClassLoader
  ): Option[URL] =
    Option(classLoader.getResource(filename))
end Resources

package tikrana.util

import Extensions.*
import Types.*

import java.io.{FileNotFoundException, InputStream}
import java.net.URL
import scala.util.Try

object Resources:
  def readResourceText(
      path: Filename,
      encoding: String = "UTF-8"
  ): Try[String] =
    readResource(path)
      .map(String(_, encoding))

  def readResource(path: Filename): Try[Array[Byte]] =
    withResourceStream(path)(_.readAllBytes())
      .mapFailure(Fault(s"Error while reading resource '$path'", _))

  def withResourceStream[A](path: Filename)(f: InputStream => A): Try[A] =
    getResourceAsStream(path)
      .toTry(FileNotFoundException(s"No such resource: '$path'"))
      .flatMap(is => Try(f(is)))

  def openResource(path: Filename): Try[InputStream] =
    getResourceAsStream(path)
      .toTry(Fault(s"No such resource $path"))

  def getResourceAsStream(
      path: Filename,
      classLoader: => ClassLoader = ctxClassLoader
  ): Option[InputStream] =
    Option(classLoader.getResourceAsStream(path))

  def getResource(
      path: Filename,
      classLoader: => ClassLoader = ctxClassLoader
  ): Option[URL] =
    Option(classLoader.getResource(path))

  // TODO Make ctxClassLoader a package-level def
  def ctxClassLoader: ClassLoader =
    Thread.currentThread().getContextClassLoader
end Resources
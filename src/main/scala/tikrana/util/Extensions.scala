package tikrana.util

import Fault.*

object IO:
  import java.io.Closeable
  import scala.util.Using

  type Filename = String
  type Extension = String
  type ByteArray = Array[Byte]

  extension [A <: Closeable](closeable: A)
    def use[B](f: A => B): Either[Throwable, B] =
      Using(closeable)(f).toEither

object Net:
  type Port = Int
  type NetAddress = String
  type DirectoryName = String

object Resources:
  import IO.*
  import java.net.URL
  import java.io.{FileNotFoundException, InputStream}

  def readResourceText(
      path: Filename,
      encoding: String = "UTF-8"
  ): Either[Fault, String] =
    readResource(path)
      .map(String(_, encoding))

  def readResource(path: Filename): Either[Fault, Array[Byte]] =
    withResourceStream(path)(_.readAllBytes())
      .mapLeft(Fault(s"Error while reading resource '$path'", _))

  def withResourceStream[A](
      path: Filename
  )(
      f: InputStream => A
  ): Either[Throwable, A] =
    getResourceAsStream(path)
      .toRight(FileNotFoundException(s"No such resource: '$path'"))
      .flatMap(_.use(f))

  def openResource(path: Filename): Either[Fault, InputStream] =
    getResourceAsStream(path)
      .toRight(Fault(s"No such resource $path"))

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

  def ctxClassLoader: ClassLoader =
    Thread.currentThread().getContextClassLoader()

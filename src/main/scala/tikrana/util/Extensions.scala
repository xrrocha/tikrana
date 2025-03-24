package tikrana.util

import ErrCond.*

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

object Resources:
  import IO.*
  import java.net.URL
  import java.io.{FileNotFoundException, InputStream}

  def readResourceText(
      path: Filename,
      encoding: String = "UTF-8"
  ): Either[ErrCond, String] =
    readResource(path)
      .map(String(_, encoding))

  def readResource(path: Filename): Either[ErrCond, Array[Byte]] =
    withResourceStream(path)(_.readAllBytes())
      .mapLeft(ErrCond(s"Error while reading resource '$path'", _))

  def withResourceStream[A](
      path: Filename
  )(
      f: InputStream => A
  ): Either[Throwable, A] =
    getResourceAsStream(path)
      .toRight(FileNotFoundException(s"No such resource: '$path'"))
      .flatMap(_.use(f))

  def openResource(path: Filename): Either[ErrCond, InputStream] =
    getResourceAsStream(path)
      .toRight(ErrCond(s"No such resource $path"))

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

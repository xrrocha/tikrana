package tikrana.util

import tikrana.util.Extensions.*
import tikrana.util.Types.*

import java.io.{FileNotFoundException, InputStream}
import java.net.URL

import scala.util.{Failure, Success, Try, Using}

object Resources:

  def getResource(
      filename: Filename,
      classLoader: => ClassLoader = Utils.ctxClassLoader
  ): Option[URL] =
    Option(classLoader.getResource(filename))

  def getResourceAsStream(
      filename: Filename,
      classLoader: => ClassLoader = Utils.ctxClassLoader
  ): Option[InputStream] =
    Option(classLoader.getResourceAsStream(filename))

  def openResource(filename: Filename): Try[InputStream] =
    getResourceAsStream(filename)
      .toTry(FileNotFoundException(s"No such resource $filename"))

  def readResource(filename: Filename): Try[Array[Byte]] =
    for
      inputStream <- openResource(filename)
      readLambda = (is: InputStream) => is.readAllBytes()
      bytes <- Using(inputStream)(readLambda)
    yield bytes

  def readResourceText(
      filename: Filename,
      encoding: String = "UTF-8"
  ): Try[String] =
    for
      bytes <- readResource(filename)
      text = String(bytes, encoding)
    yield text
end Resources

package tikrana.util

import tikrana.util.Types.*
import tikrana.util.extension.OptionExtensions.*
import tikrana.util.extension.TryExtensions.*

import java.io.{FileNotFoundException, InputStream}
import java.net.URL
import scala.util.{Try, Using}

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
      readBytes = (is: InputStream) => is.readAllBytes()
      bytes <- Using(inputStream)(readBytes)
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

package tikrana.util.extension

import tikrana.util.Types.ByteArray

import java.io.InputStream
import java.net.URL

import scala.util.{Try, Using}

object UrlExtensions:
  extension (u: URL)
    def readBytes(): Try[ByteArray] =
      for
        inputStream <- Try(u.openStream())
        readerLambda = (is: InputStream) => is.readAllBytes()
        bytes <- Using(inputStream)(readerLambda)
      yield bytes

    def readText(encoding: String = "UTF-8"): Try[String] =
      for
        bytes <- u.readBytes()
        string = String(bytes, encoding)
      yield string

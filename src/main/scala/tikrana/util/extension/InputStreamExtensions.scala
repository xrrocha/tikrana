package tikrana.util.extension

import tikrana.util.Types.ByteArray

import java.io.InputStream

import scala.util.{Try, Using}

object InputStreamExtensions:
  extension (inputStream: InputStream)
    def readBytes(): Try[ByteArray] =
      Using(inputStream)(_.readAllBytes())
    def readText(encoding: String = "UTF-8"): Try[String] =
      inputStream
        .readBytes()
        .map(String(_, encoding))

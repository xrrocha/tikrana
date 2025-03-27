package tikrana.web

import tikrana.util.Utils.*

import java.io.File

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

// TODO Add smart constructor to config companion object
// TODO Configure executor for WebServer (w/virtual threads)
case class Config(
    protocol: Protocol = Protocol.HTTP,
    address: NetAddress = "127.0.0.0",
    port: NetPort = 1960,
    stopDelay: Int = 0,
    mimeTypes: Map[Extension, MimeType] = Map.empty,
    baseDirectory: Option[Directory] =
      Some(File(System.getProperty("user.dir"))),
    basePackage: Option[Path] = None,
    classLoader: ClassLoader = Thread.currentThread.getContextClassLoader
):

  lazy val uri = s"${protocol.scheme}://$address:$port"
end Config

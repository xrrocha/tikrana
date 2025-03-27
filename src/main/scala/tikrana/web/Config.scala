package tikrana.web

import tikrana.util.Fault
import tikrana.util.Utils.*

import java.io.File
import java.net.InetSocketAddress

import scala.util.Try
import tikrana.util.Resources.getResource
import scala.annotation.threadUnsafe

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

// TODO Configure executor for WebServer (w/virtual threads)
case class Config private (
    protocol: Protocol,
    address: NetAddress,
    port: NetPort,
    stopDelay: Int,
    mimeTypes: Map[Extension, MimeType],
    baseDirectory: Option[Directory],
    basePackage: Option[Path],
    classLoader: ClassLoader
):
  lazy val uri = s"${protocol.scheme}://$address:$port"
end Config

object Config:
  def apply(
      protocol: Protocol = Protocol.HTTP,
      address: NetAddress = "127.0.0.0",
      port: NetPort = 1960,
      stopDelay: Int = 0,
      mimeTypes: Map[Extension, MimeType] = Map.empty,
      baseDirectory: Option[Directory] = Some(
        File(System.getProperty("user.dir"))
      ),
      basePackage: Option[Path] = None,
      classLoader: ClassLoader = Thread.currentThread.getContextClassLoader
  ): Try[Config] =
    Try:
      try
        InetSocketAddress(address, port)
          .also: isa =>
            if isa.isUnresolved then
              logger.warning(s"Address '$address' is unresolved")
      catch
        case e: Exception =>
          throw Fault(s"Invalid address/port: '$address:$port'", e)

      require(stopDelay >= 0, "Stop delay cannot be negative")

      require(
        baseDirectory.isDefined || basePackage.isDefined,
        "Either base directory and/or base package must be defined"
      )

      require(
        baseDirectory.isEmpty ||
          baseDirectory
            .filter(dir => dir.isDirectory() && dir.canRead())
            .isDefined,
        s"Unreadable base directory: '${baseDirectory.get}'"
      )

      require(
        basePackage.isEmpty ||
          basePackage
            .filter(pkg => getResource(s"$pkg/", classLoader).isDefined)
            .isDefined,
        s"Unreadable base package: '${basePackage.get}'"
      )

      new Config(
        protocol,
        address,
        port,
        stopDelay,
        mimeTypes,
        baseDirectory,
        basePackage,
        classLoader
      )
  end apply
end Config

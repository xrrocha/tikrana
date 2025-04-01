package tikrana.web

import Types.*

import tikrana.util.Fault
import tikrana.util.Resources.getResource
import tikrana.util.Utils.*

import java.io.File
import java.net.InetSocketAddress
import scala.util.Try

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

// TODO Configure executor for WebServer (w/virtual threads)
case class Config private (
    protocol: Protocol,
    address: InetSocketAddress,
    backlog: Int,
    stopDelay: Int,
    mimeTypes: Map[Extension, MimeType],
    baseDirectory: Option[Directory],
    basePackage: Option[Path],
    classLoader: ClassLoader
):
  lazy val uri =
    s"${protocol.scheme}://${address.getHostString}:${address.getPort}"
end Config

object Config:
  // TODO Collect all errors in a single exception
  def apply(
      protocol: Protocol = Protocol.HTTP,
      address: NetAddress = "127.0.0.1",
      port: NetPort = 1960,
      backlog: Int = 0,
      stopDelay: Int = 0,
      mimeTypes: Map[Extension, MimeType] = Map.empty,
      baseDirectory: Option[DirectoryName] = Some(
        System.getProperty("user.dir")
      ),
      basePackage: Option[Path] = None,
      classLoader: ClassLoader = ctxClassLoader
  ): Try[Config] =
    Try:
        val inetSocketAddress =
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
              .map(File(_))
              .exists(dir => dir.isDirectory && dir.canRead),
          s"Unreadable base directory: '${baseDirectory.get}'"
        )

        require(
          basePackage.isEmpty ||
            basePackage.exists(pkg =>
              getResource(s"$pkg/", classLoader).isDefined
            ),
          s"Unreadable base package: '${basePackage.get}'"
        )

        new Config(
          protocol,
          inetSocketAddress,
          backlog,
          stopDelay,
          mimeTypes,
          baseDirectory.map(File(_)),
          basePackage,
          classLoader
        )
  end apply
end Config

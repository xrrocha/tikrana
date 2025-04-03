package tikrana.web

import tikrana.util.Fault
import tikrana.util.Resources.getResource
import tikrana.util.Utils.*
import tikrana.web.Types.*

import java.io.File
import java.net.InetSocketAddress
import scala.util.Try

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

case class HandlerConfig private[web] (
    classLoader: ClassLoader,
    mimeTypes: Map[Extension, MimeType],
    loaders: Seq[ResourceLoader],
    indexFiles: IndexFiles
)

// TODO Configure executor for WebServer (w/virtual threads)
case class ServerConfig private (
    protocol: Protocol,
    address: InetSocketAddress,
    backlog: Int,
    stopDelay: Int,
    handlerConfig: HandlerConfig
):
  lazy val uri =
    s"${protocol.scheme}://${address.getHostString}:${address.getPort}"
end ServerConfig

object ServerConfig:
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
      classLoader: ClassLoader = ctxClassLoader,
      indexFilenames: Seq[Path] = IndexFiles.defaultBaseNames,
      indexFileExtensions: Seq[Path] = IndexFiles.defaultExtensions
  ): Try[ServerConfig] =
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

        require(
          indexFilenames.nonEmpty,
          "Index file names cannot be empty"
        )
        require(
          indexFileExtensions.nonEmpty,
          "Index file extensions cannot be empty"
        )

        val indexFiles = IndexFiles(
          indexFilenames,
          indexFileExtensions
        )

        new ServerConfig(
          protocol,
          inetSocketAddress,
          backlog,
          stopDelay,
          HandlerConfig(
            classLoader,
            mimeTypes,
            Seq(
              baseDirectory
                .map(dir => FileLoader(File(dir), indexFiles)),
              basePackage
                .flatMap(pkg => getResource(s"$pkg/", classLoader))
                .map: url =>
                  val uri = url.toURI
                  if uri.getScheme == "file" then
                    FileLoader(File(uri), indexFiles)
                  else
                    ClasspathLoader(
                      basePackage.get,
                      classLoader,
                      indexFiles
                    )
            )
              .filter(_.isDefined)
              .map(_.get),
            indexFiles
          )
        )
  end apply
end ServerConfig

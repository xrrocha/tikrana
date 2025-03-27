package tikrana.web

import com.sun.net.httpserver.*
import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.ResourceLoader.DefaultMimeType
import tikrana.web.WebServer.defaultBaseDirectories

import java.io.{File, FileInputStream}
import java.net.InetSocketAddress
import java.util.logging.{Level, Logger}
import java.util.logging.Level.*
import scala.collection.mutable
import scala.util.{Success, Try}

type Path = String

type MimeType = String
type FileType = String
type HeaderName = String
type HeaderValue = String

enum Protocol:
  case HTTP, HTTPS
  def scheme: String = toString.toLowerCase
end Protocol

object WebServer:
  val defaultBaseDirectories: Seq[Directory] = Seq(
    File(System.getProperty("user.dir"))
  )
  // TODO Fix webserver logger visibility
  val logger: Logger = Logger.getLogger("tikrana.web.WebServer")

import tikrana.web.WebServer.*

// TODO Add smart constructor to config companion object
case class WebServerConfig(
    protocol: Protocol = Protocol.HTTP,
    address: NetAddress,
    port: NetPort,
    stopDelay: Int = 0,
    // TODO Configure executor for WebServer (w/virtual threads)

    baseDirectories: Seq[Directory] = defaultBaseDirectories,
    basePackages: Seq[Path] = Seq.empty,
    mimeTypes: Map[Extension, MimeType] = Map.empty
):

  lazy val uri = s"${protocol.scheme}://$address:$port"
end WebServerConfig

case class WebServer(config: WebServerConfig):
  private val rootHandler = RootHandler(config)
  private var webServer = createServer(rootHandler)

  def start(): Try[WebServer] =
    webServer
      .peek(_.start())
      .map(_ => this)

  def stop(): Try[WebServer] =
    webServer.peek(_.stop(config.stopDelay))
    webServer = createServer(rootHandler)
    webServer.map(_ => this)

  private def createServer(
      rootHandler: RootHandler
  ): Try[HttpServer] =
    Try:
      HttpServer.create(InetSocketAddress(config.address, config.port), 0)
    .peek: server =>
      // TODO Use config-provided executor
      server.setExecutor(null)
      server.createContext("/", rootHandler)
      logger.fine(s"Listening on ${config.uri}")
    .mapFailure: t =>
      Fault(s"Error creating web webServer", t)
        .logAsWarning(logger)
end WebServer

enum HttpCode(val code: Int, val message: String):
  case OK extends HttpCode(200, "OK")
  case BAD_REQUEST extends HttpCode(400, "Bad Request")
  case UNAUTHORIZED extends HttpCode(401, "Not Authorized")
  case NOT_FOUND extends HttpCode(404, "Not Found")
end HttpCode

case class Result(
    httpCode: HttpCode,
    contents: ByteArray,
    mimeType: MimeType = DefaultMimeType,
    headers: Map[HeaderName, Seq[HeaderValue]] = Map.empty
)
object Result:
  val NotFound: Result = Result(
    HttpCode.NOT_FOUND,
    "<h1>Not found</h1>".getBytes(),
    "text/html"
  )

trait Resource:
  def contents(): Try[ByteArray]
  def hasChangedSince(time: Millis): Boolean

trait Handler:
  def handle(exchange: HttpExchange): Try[Result]
object Handler:
  val NotFound: Handler = _ => Success(Result.NotFound)

trait ResourceLoader:
  def load(path: Path): Option[Resource]
object ResourceLoader:
  val DefaultMimeType = "application/octet-stream"

class RootHandler(config: WebServerConfig) extends HttpHandler:
  case class Entry(resource: Resource, handler: Handler)
  private val cache = mutable.Map[Path, (Entry, Millis)]()

  private val loaders = Seq[ResourceLoader](
    FileLoader(config.baseDirectories),
    // TODO Make class loader configurable
    ClasspathLoader(config.basePackages, ctxClassLoader)
  )
  private val mimeTypes: Map[FileType, MimeType] =
    DefaultMimeTypes ++ config.mimeTypes

  override def handle(exchange: HttpExchange): Unit =
    getHandler(exchange)
      .handle(exchange)
      .map: result =>
        exchange.sendResponseHeaders(
          result.httpCode.code,
          result.contents.length
        )
        exchange.getResponseBody
          .use: out =>
            out.write(result.contents)
            out.flush()
          .mapFailure: t =>
            Fault(s"I/O error handling ${exchange.getRequestURI}", t)
              .logAsWarning(WebServer.logger)
      .peekFailure: exc =>
        WebServer.logger.logt(FINE, s"Error handling exchange", exc)
  end handle

  def getHandler(exchange: HttpExchange): Handler =
    val path = getPath(exchange)
    cache.get(path) match
      case Some((Entry(resource, handler), time)) =>
        if resource.hasChangedSince(time) then buildHandlerFor(path, resource)
        else handler
      case None =>
        loadResource(path)
  end getHandler

  private def loadResource(path: Path): Handler =
    val loadedResource =
      // TODO Ascertain LazyList is actually needed here
      LazyList
        .from(loaders)
        .map(_.load(path))
        .find(_.isDefined)
        .flatten
    loadedResource match
      case Some(resource) =>
        buildHandlerFor(path, resource)
      case None =>
        logger.fine(s"Resource not found: $path")
        Handler.NotFound
  end loadResource

  def getPath(exchange: HttpExchange): Path =
    exchange.getRequestURI.getPath
      .substring(1)
      .let: path =>
        // TODO Solve directory/index file
        if path == "" then "index.html"
        else if path.endsWith("/") then path + "index.html"
        else path
      .also: path =>
        logger.fine(s"Request for $path")

  private def buildHandlerFor(path: Path, resource: Resource): Handler =
    val mimeType =
      getFileType(path) match
        case Some(fileType) =>
          mimeTypes.getOrElse(fileType, DefaultMimeType)
        case None =>
          DefaultMimeType
    val handler: Handler = _ =>
      resource
        .contents()
        .map: contents =>
          Result(HttpCode.OK, contents, mimeType)
        .peekFailure: exc =>
          WebServer.logger.logt(FINE, s"Error retrieving resource '$path'", exc)
    cache(path) = (Entry(resource, handler), System.currentTimeMillis)
    handler
  end buildHandlerFor

  private def getFileType(path: Path): Option[FileType] =
    // TODO Move filename extension logic to some util object
    val pos = path.lastIndexOf('.')
    if pos < 0 then None
    else Some(path.substring(pos + 1))

end RootHandler

class FileResource(file: File) extends Resource:
  override def contents(): Try[ByteArray] =
    Try:
      FileInputStream(file)
    .flatMap: is =>
      is.use(_.readAllBytes())
    .mapFailure: t =>
      Fault(s"Error reading file '${file.getAbsolutePath}'", t)
        .logAsWarning(WebServer.logger)

  override def hasChangedSince(time: Millis): Boolean =
    file.lastModified() > time
end FileResource

class FileLoader(val directories: Seq[Directory]) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
    directories
      .map: directory =>
        val file = File(directory, path)
        // TODO Consider mapping !file.canRead() to UNAUTHORIZED
        if file.exists() && file.canRead then Some(file)
        else None
      .find(_.isDefined)
      .flatten
      // TODO Check whether file is directory and has index file
      .map(FileResource(_))
end FileLoader

class ClasspathLoader(
    val packages: Seq[Path],
    val classLoader: ClassLoader
) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
    packages
      .map(pkg => getResource(s"$pkg/$path", classLoader))
      .find(_.isDefined)
      .flatten
      .map: url =>
        val uri = url.toURI
        if uri.getScheme == "file" then FileResource(new File(uri))
        else
          new Resource:
            override def contents(): Try[ByteArray] =
              Try(url.openStream().readAllBytes())
                .mapFailure: t =>
                  Fault(s"Error reading resource: '$path'", t)
                    .logAsWarning(WebServer.logger)
            override def hasChangedSince(time: Millis): Boolean =
              false
end ClasspathLoader

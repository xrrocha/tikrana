package tikrana.web

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.ResourceLoader.DefaultMimeType

import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.io.{File, FileInputStream}
import java.util.logging.{Level, Logger}
import java.util.logging.Level.FINE

import scala.util.{Success, Try}
import scala.collection.mutable

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

class RootHandler(config: Config) extends HttpHandler:
  case class Entry(resource: Resource, handler: Handler)
  private val cache = mutable.Map[Path, (Entry, Millis)]()

  private val loaders: Seq[ResourceLoader] = Seq(
    config.baseDirectory.map(FileLoader(_)),
    config.basePackage.map(ClasspathLoader(_, config.classLoader))
  )
    .filter(_.isDefined)
    .map(_.get)

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
              .logAsWarning(logger)
      .peekFailure: exc =>
        logger.logt(FINE, s"Error handling exchange", exc)
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
          logger.logt(FINE, s"Error retrieving resource '$path'", exc)
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
        .logAsWarning(logger)

  override def hasChangedSince(time: Millis): Boolean =
    file.lastModified() > time
end FileResource

class FileLoader(val directory: Directory) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
    File(directory, path)
      .let: file =>
        // TODO Consider mapping !file.canRead() to UNAUTHORIZED
        if file.exists() && file.canRead then Some(file)
        else None
      .map(FileResource(_))
end FileLoader

class ClasspathLoader(
    val packageName: Path,
    val classLoader: ClassLoader
) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
    getResource(s"$packageName/$path", classLoader)
      .map: url =>
        val uri = url.toURI
        if uri.getScheme == "file" then FileResource(new File(uri))
        else
          new Resource:
            override def contents(): Try[ByteArray] =
              Try(url.openStream().readAllBytes())
                .mapFailure: t =>
                  Fault(s"Error reading resource: '$path'", t)
                    .logAsWarning(logger)
            override def hasChangedSince(time: Millis): Boolean =
              false
end ClasspathLoader

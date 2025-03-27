package tikrana.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.File
import java.io.FileInputStream
import java.util.logging.Level
import java.util.logging.Level.FINE
import java.util.logging.Logger
import scala.collection.mutable
import scala.util.Success
import scala.util.Try
import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.ResourceLoader.DefaultMimeType

trait Resource:
  def contents(): Try[ByteArray]
  def stillExists(): Boolean
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
          // TODO Some handlers may not write a fixed number
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
      case Some((Entry(resource, sameHandler), time)) =>
        if !resource.stillExists() then
          cache.remove(path)
          Handler.NotFound
        else if resource.hasChangedSince(time) then
          buildHandlerFor(path, resource)
        else
          sameHandler
      case None =>
        buildHandlerFor(path)
  end getHandler

  private def buildHandlerFor(path: Path): Handler =
    val loadedResource =
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
  end buildHandlerFor

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
          logger.logt(FINE, s"Error reading resource '$path'", exc)
    cache(path) = (
      Entry(resource, handler),
      System.currentTimeMillis
    )
    handler
  end buildHandlerFor

  private def getFileType(path: Path): Option[FileType] =
    path.extension
end RootHandler

// TODO Remove entry when file has vanished
class FileResource(file: File) extends Resource:
  override def contents(): Try[ByteArray] =
    Try(FileInputStream(file))
      .flatMap(_.readBytes())
      .mapFailure: t =>
        Fault(s"Error reading file '${file.getAbsolutePath}'", t)
          .logAsWarning(logger)

  override def stillExists(): Boolean =
    // TODO Consider mapping !file.canRead() to UNAUTHORIZED
    file.exists() && file.canRead()
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
            override def stillExists(): Boolean =
              true
            override def hasChangedSince(time: Millis): Boolean =
              false
end ClasspathLoader

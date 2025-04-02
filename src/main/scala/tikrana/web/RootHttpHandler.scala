package tikrana.web

import Types.*

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.WebResourceLoader.DefaultMimeType

import java.io.{File, FileInputStream}
import java.net.URL
import java.util.logging.Level
import java.util.logging.Level.FINE
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

import scala.collection.mutable
import scala.util.{Success, Try}

trait ExchangeHandler:
  // no-op for now, will server dynamic content later on
  def handle(exchange: HttpExchange): Try[Result]
object ExchangeHandler:
  val NotFound: ExchangeHandler = _ => Success(Result.NotFound)

trait WebResource:
  def contents(): Try[ByteArray]
  def stillExists(): Boolean
  def hasChangedSince(time: Millis): Boolean

trait WebResourceLoader:
  def load(path: Path): Option[WebResource]
object WebResourceLoader:
  val DefaultMimeType = "application/octet-stream"

object RootHttpHandler:
  def indexFile: Path = "index.html"
end RootHttpHandler

class RootHttpHandler(config: HandlerConfig) extends HttpHandler:
  case class Entry(resource: WebResource, handler: ExchangeHandler)
  private val cache = mutable.Map[Path, (Entry, Millis)]()

  private val loaders: Seq[WebResourceLoader] =
    Seq(config.baseDirectory, config.basePackage)
      .filter(_.isDefined)
      .map(_.get)

  private val mimeTypes: Map[FileType, MimeType] =
    DefaultMimeTypes ++ config.mimeTypes

  override def handle(exchange: HttpExchange): Unit =
    getHandlerFor(exchange)
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
        logger.logl(FINE, s"Error handling exchange", exc)
  end handle

  def getHandlerFor(exchange: HttpExchange): ExchangeHandler =
    val path = getPath(exchange)
    cache.get(path) match
      case Some((Entry(resource, sameHandler), time)) =>
        if !resource.stillExists() then
          // TODO Remove all cache entries descending from path
          cache.remove(path)
          ExchangeHandler.NotFound
        else if resource.hasChangedSince(time) then
          buildHandlerFor(path, resource)
        else sameHandler
      case None =>
        buildHandlerFor(path)
  end getHandlerFor

  def getPath(exchange: HttpExchange): Path =
    exchange.getRequestURI.getPath.substring(1)

  private def buildHandlerFor(path: Path): ExchangeHandler =
    val loadedResource =
      LazyList
        .from(loaders)
        .map(_.load(path))
        .find(_.isDefined)
        .flatten
    loadedResource match
      case Some(resource) =>
        buildHandlerFor(path, resource)
          .also: handler =>
            // TODO Evict cache entries after some time-to-live
            cache(path) = (
              Entry(resource, handler),
              System.currentTimeMillis
            )
      case None =>
        logger.finer(s"Resource not found: $path")
        ExchangeHandler.NotFound
  end buildHandlerFor

  private def buildHandlerFor(
      path: Path,
      resource: WebResource
  ): ExchangeHandler =
    val mimeType =
      getFileType(path) match
        case Some(fileType) =>
          mimeTypes.getOrElse(fileType, DefaultMimeType)
        case None =>
          DefaultMimeType
    _ =>
      resource
        .contents()
        .map: contents =>
          Result(HttpCode.OK, contents, mimeType)
        .peekFailure: exc =>
          logger.logl(FINE, s"Error reading resource '$path'", exc)
  end buildHandlerFor

  private def getFileType(path: Path): Option[FileType] =
    path.extension
end RootHttpHandler

// TODO Pass default indexFile name as a parameter
class FileLoader(val baseDirectory: Directory) extends WebResourceLoader:
  override def load(path: Path): Option[WebResource] =
    val file =
      if path.isEmpty then baseDirectory
      else File(baseDirectory, path)
    if !(file.exists() && file.canRead) then None
    else if file.isFile then Some(FileResource(file))
    else // Directory
      val indexFile = File(file, RootHttpHandler.indexFile)
      if indexFile.isFile && indexFile.canRead then
        Some(FileResource(indexFile))
      else Some(FileResource(file))
end FileLoader

class FileResource(file: File) extends WebResource:
  override def contents(): Try[ByteArray] =
    Try:
        if file.isFile then FileInputStream(file).readAllBytes()
        else
          directory2Html(file.listFiles().toList.map(_.getName))
            .getBytes("UTF-8")
      .mapFailure: t =>
        Fault(s"Error reading file '${file.getAbsolutePath}'", t)
          .logAsWarning(logger)
  override def stillExists(): Boolean =
    file.exists() && file.canRead
  override def hasChangedSince(time: Millis): Boolean =
    file.lastModified() > time
end FileResource

// TODO Check for resource (package) directories
class ClasspathLoader(
    val packageName: Path,
    val classLoader: ClassLoader
) extends WebResourceLoader:
  override def load(path: Path): Option[WebResource] =
    def get(suffix: String): Option[URL] =
      getResource(s"$packageName/$path$suffix", classLoader)

    get("/")
      .map: dirUrl =>
        get(s"/${RootHttpHandler.indexFile}")
          .map(fileUrl => "file" -> fileUrl)
          .orElse(Some("dir" -> dirUrl))
      .orElse:
        get(s"/$path")
          .map(fileUrl => Some("file" -> fileUrl))
      .flatten
      .map: (kind, url) =>
        (kind, url) match
          case ("file", url) => FileClasspathResource(url)
          case ("dir", url)  => DirectoryClasspathResource(url)
  end load

  class DirectoryClasspathResource(url: URL) extends WebResource:
    override def contents(): Try[ByteArray] =
      // TODO Collect all jar entries & render as dir html
      ???
    override def stillExists(): Boolean =
      true
    override def hasChangedSince(time: Millis): Boolean =
      false
  end DirectoryClasspathResource

  class FileClasspathResource(url: URL) extends WebResource:
    override def contents(): Try[ByteArray] =
      Try(url.openStream().readAllBytes())
        .mapFailure: t =>
          Fault(s"Error reading resource: '$url'", t)
            .logAsWarning(logger)
    override def stillExists(): Boolean =
      true
    override def hasChangedSince(time: Millis): Boolean =
      false
  end FileClasspathResource
end ClasspathLoader

// TODO Move html functions to proper location
def directory2Html(filenames: => Seq[Filename]): String =
  filenames
    .map(escapeHtml)
    .map: filename =>
      s"  <a href='$filename'>$filename</a>"
    .mkString(
      start = s"""
                 |<html>
                 |<head>
                 |  <meta charset='UTF-8'>
                 |  <title>Directory listing</title>
                 |</head>
                 |<body>
                 |  <h1>Directory listing</h1>
                 |""".stripMargin,
      sep = "<br>",
      end = """
              |</body>
              |</html>
              |""".stripMargin
    )
end directory2Html

// TODO Actually escape html
// TODO Define an escaping string interpolator for html"..."
def escapeHtml(html: String): String = html

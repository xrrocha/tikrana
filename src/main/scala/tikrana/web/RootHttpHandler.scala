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
import scala.util.{Failure, Success, Try}

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
  private val cache = WebCache(config: HandlerConfig)

  private val mimeTypes: Map[FileType, MimeType] =
    DefaultMimeTypes ++ config.mimeTypes

  override def handle(exchange: HttpExchange): Unit =
    val path = getPath(exchange)

    val outcome =
      for
        cachedEntry <- cache.getPayloadFor(path)

        result = cachedEntry match
          case Some(payload) =>
            Result(HttpCode.OK, payload, getMimeTypeFor(path))
          case None =>
            logger.logFiner(s"Request path not found: $path")
            Result.NotFound

        _ <- Try:
            exchange.sendResponseHeaders(
              result.httpCode.code,
              result.contents.length
            )
            exchange.getResponseBody.write(result.contents)
            exchange.close()
      yield ()

    val uri = exchange.getRequestURI
    outcome.match
        case Success(_) =>
          logger.logFiner(s"Handled request: $uri")
        case Failure(err) =>
          logger.logFine(err, s"Error handling request: $uri")
  end handle

  private[web] def getPath(exchange: HttpExchange): Path =
    exchange.getRequestURI.getPath.substring(1).toLowerCase

  private[web] def getMimeTypeFor(path: Path): MimeType =
    getFileType(path).flatMap(mimeTypes.get).getOrElse(DefaultMimeType)

  private[web] def getFileType(path: Path): Option[FileType] =
    path.extension.map(_.toLowerCase)
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

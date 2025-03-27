package tikrana.web

import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.ResourceLoader.DefaultMimeType

import java.io.{File, FileInputStream}
import java.util.logging.Level
import java.util.logging.Level.FINE
import scala.collection.mutable
import scala.util.{Success, Try}

trait Resource:
  def contents(): Try[ByteArray]
  def stillExists(): Boolean
  def hasChangedSince(time: Millis): Boolean

trait Handler:
  // Handler.handle is a no-op for now
  // will server dynamic content later on
  def handle(exchange: HttpExchange): Try[Result]
object Handler:
  val NotFound: Handler = _ => Success(Result.NotFound)

trait ResourceLoader:
  def load(path: Path): Option[Resource]
object ResourceLoader:
  val DefaultMimeType = "application/octet-stream"

object RootHandler:
  def indexFile: Path = "index.html"
end RootHandler

class RootHandler(config: Config) extends HttpHandler:
  case class Entry(resource: Resource, handler: Handler)
  private val cache = mutable.Map[Path, (Entry, Millis)]()

  private val loaders: Seq[ResourceLoader] = Seq(
    config.baseDirectory.map(FileLoader(_)),
    config.basePackage.flatMap: packageName =>
      getResource(s"$packageName/")
        .map: url =>
          val uri = url.toURI
          if uri.getScheme == "file" then FileLoader(File(uri))
          else ClasspathLoader(packageName, config.classLoader)
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
          // TODO Remove all cache entries descending from path
          cache.remove(path)
          Handler.NotFound
        else if resource.hasChangedSince(time) then
          buildHandlerFor(path, resource)
        else sameHandler
      case None =>
        buildHandlerFor(path)
  end getHandler

  def getPath(exchange: HttpExchange): Path =
    exchange.getRequestURI.getPath.substring(1)

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
        logger.finer(s"Resource not found: $path")
        Handler.NotFound
  end buildHandlerFor

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

// TODO Pass default indexFile name as a parameter
class FileLoader(val baseDirectory: Directory) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
    val file =
      if path.isEmpty then baseDirectory
      else File(baseDirectory, path)
    if !(file.exists() && file.canRead) then None
    else if file.isFile then Some(FileResource(file))
    else // Directory
      val indexFile = File(file, RootHandler.indexFile)
      if indexFile.isFile && indexFile.canRead then
        Some(FileResource(indexFile))
      else Some(FileResource(file))
end FileLoader

class FileResource(file: File) extends Resource:
  override def contents(): Try[ByteArray] =
    Try:
      if file.isFile then FileInputStream(file).readAllBytes()
      else
        val directoryName =
          if file.getName == "" then "/"
          else file.getName
        directory2Html(
          directoryName,
          file.listFiles().toList.map(_.getName)
        )
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

// Move html functions to proper location
// TODO Actually escape html
def escapeHtml(html: String): String = html

def directory2Html(
    directoryName: DirectoryName,
    children: => Seq[Filename]
): String =
  val escapedDirectoryName = escapeHtml(directoryName)
  children
    .map: childName =>
      val escapedName = escapeHtml(childName)
      s"  <a href='$escapedName'>$escapedName</a>"
    .mkString(
      start = s"""
                 |<html>
                 |<head>
                 |  <meta charset='UTF-8'>
                 |  <title>Directory listing for $escapedDirectoryName</title>
                 |</head>
                 |<body>
                 |  <h1>Directory listing for $escapedDirectoryName</h1>
                 |""".stripMargin,
      sep = "<br>",
      end = """
              |</body>
              |</html>
              |""".stripMargin
    )
end directory2Html

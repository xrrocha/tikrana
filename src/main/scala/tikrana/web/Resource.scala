package tikrana.web

import Types.*

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*

import java.io.{File, FileInputStream}
import java.net.URL
import java.util.logging.Level
import java.util.logging.Level.FINE
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

trait Resource:
  def contents(): Try[ByteArray]
  def stillExists(): Boolean
  def lastModified(): Millis

  def hasVanished(): Boolean = !stillExists()
  def hasChangedSince(time: Millis): Boolean = lastModified() > time

trait ResourceLoader:
  def load(path: Path): Option[Resource]

// TODO Pass default indexFile name as a parameter
class FileLoader(val baseDirectory: Directory) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
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

class FileResource(file: File) extends Resource:
  override def contents(): Try[ByteArray] =
    Try:
        if file.isFile then FileInputStream(file).readAllBytes()
        else
          directory2Html(file.listFiles().toList.map(_.getName))
            .getBytes("UTF-8")
      .mapFailure: t =>
        Fault(s"Error reading file '${file.getAbsolutePath}'", t)
          .logAsWarning(logger)
  override def stillExists(): Boolean = file.exists() && file.canRead
  override def lastModified(): Millis = file.lastModified()
end FileResource

// TODO Check for resource (package) directories
class ClasspathLoader(
    val packageName: Path,
    val classLoader: ClassLoader
) extends ResourceLoader:
  override def load(path: Path): Option[Resource] =
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

  class DirectoryClasspathResource(url: URL) extends Resource:
    override def contents(): Try[ByteArray] =
      // TODO Collect all jar entries & render as dir html
      ???
    override def stillExists(): Boolean =
      true
    override def lastModified(): Millis =
      System.currentTimeMillis()
  end DirectoryClasspathResource

  class FileClasspathResource(url: URL) extends Resource:
    override def contents(): Try[ByteArray] =
      Try(url.openStream().readAllBytes())
        .mapFailure: t =>
          Fault(s"Error reading resource: '$url'", t)
            .logAsWarning(logger)
    override def stillExists(): Boolean =
      true
    override def lastModified(): Millis =
      System.currentTimeMillis()
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
package tikrana.web

import tikrana.util.Utils.*
import tikrana.web.Types.*

import scala.util.Try

object ResourceLoader:
  private val slashRemovers = Seq("^/+".r, "/+$".r)
  def removeSlashes(path: Path): Path =
    slashRemovers.foldLeft(path.trim): (path, remover) =>
        remover.replaceFirstIn(path, "")

trait ResourceLoader:
  def loadResource(path: Path): Try[Option[Resource]]

trait Resource:
  def contents(): Try[ByteArray]
  def stillExists(): Boolean
  def lastModified(): Millis

  def hasVanished(): Boolean = !stillExists()
  def hasChangedSince(time: Millis): Boolean = lastModified() > time
end Resource

object IndexFiles:
  val defaultBaseNames = Seq("index", "README")
  val defaultExtensions = Seq("html", "htm", "txt")

case class IndexFiles(baseNames: Seq[Path], extensions: Seq[Path]):
  def find(exists: Path => Boolean): Option[Path] =
    find(identity)(exists)

  def find[A](map: Path => A)(exists: A => Boolean): Option[A] =
    val found =
      for
        name <- LazyList.from(baseNames)
        ext <- LazyList.from(extensions)
        candidate = map(s"$name.$ext")
        if exists(candidate)
      yield candidate
    found.headOption
end IndexFiles

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
      sep = "<br>\n",
      end = """
              |</body>
              |</html>
              |""".stripMargin
    )
end directory2Html

// TODO Actually escape html
// TODO Define an escaping string interpolator for html"..."
def escapeHtml(html: String): String = html

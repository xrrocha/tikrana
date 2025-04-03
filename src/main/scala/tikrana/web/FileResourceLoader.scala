package tikrana.web

import Types.*

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*

import java.io.{File, FileInputStream}
import java.net.{JarURLConnection, URL}
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.logging.Level.FINE
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import scala.util.{Failure, Success, Try}

class FileLoader(
    val baseDirectory: Directory,
    val indexFiles: IndexFiles
) extends ResourceLoader:
  override def loadResource(path: Path): Try[Option[Resource]] =
    Try:
     val file =
       if path.isEmpty then baseDirectory
       else File(baseDirectory, path)
 
     // Unreadable file: None
     if !(file.exists() && file.canRead) then None
     // Regular file: FileResource
     else if file.isFile then Some(FileResource(file))
     // Directory: index file (if found) or same directory
     else
       val directory = file
       val indexFile =
         indexFiles
           .find(File(directory, _)): candidate =>
             candidate.exists() && candidate.canRead
           .getOrElse(directory)
       Some(FileResource(indexFile))
end FileLoader

class FileResource(file: File) extends Resource:
  override def contents(): Try[ByteArray] =
    Try:
        if file.isFile then FileInputStream(file).readAllBytes()
        else
          val filenames =
            file
              .listFiles()
              .toList
              .filter(_.canRead)
              .map(_.getName)
          directory2Html(filenames).getBytes("UTF-8")
  override def stillExists(): Boolean = file.exists() && file.canRead
  override def lastModified(): Millis = file.lastModified()
end FileResource
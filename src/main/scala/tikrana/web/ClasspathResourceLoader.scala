package tikrana.web

import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.Types.*

import java.io.FileNotFoundException
import java.net.{JarURLConnection, URL}
import java.util.jar.JarFile
import scala.jdk.StreamConverters.*
import scala.util.Try

type EntryName = String

class ClasspathLoader(
    val packageName: Path,
    val classLoader: ClassLoader,
    val indexFiles: IndexFiles
) extends ResourceLoader:

  lazy val resourceMap: Try[Map[EntryName, Resource]] =
    ClasspathLoader.scanPackage(packageName, indexFiles)

  override def loadResource(path: Path): Try[Option[Resource]] =
    for
      map <- resourceMap
      resource <- Try(map.get(path))
    yield resource
end ClasspathLoader

object ClasspathLoader:

  def scanPackage(
      packageName: Path,
      indexFiles: IndexFiles
  ): Try[Map[Path, Resource]] =
    val now = System.currentTimeMillis()
    for
      allEntryNames: Set[EntryName] <- getAllEntryNames(packageName)

      (files: Set[Path], dirs: Set[Path]) =
        partitionFilesAndDirs(allEntryNames)

      fileResources: Set[(Path, Resource)] =
        for
          file <- files
          url <- getResource(s"$packageName/$file")
          resource = new Resource:
            override def contents(): Try[ByteArray] =
              Try(url.openStream().readAllBytes())
            override def stillExists(): Boolean = true
            override def lastModified(): Millis = now
        yield (file, resource)
      fileMap: Map[Path, Resource] = fileResources.toMap

      dirIndexFiles: Set[(Path, Resource)] =
        for
          dir <- dirs
          indexFile <- indexFiles.find(fileMap.contains)
        yield (dir, fileMap(indexFile))
      indexFileMap = dirIndexFiles.toMap
    yield fileMap ++ indexFileMap
  end scanPackage

  def getAllEntryNames(pkgName: Path): Try[Set[EntryName]] =

    val packageName = ResourceLoader.removeSlashes(pkgName)

    for
      _ <- Try:
          require(
            packageName.nonEmpty,
            s"Invalid base package name: '$pkgName'"
          )
      baseUrl <- getResource(s"$packageName/")
        .toTry:
          FileNotFoundException(s"No such package name: '$packageName'")
      connection <- Try:
          baseUrl
            .openConnection()
            .asInstanceOf[JarURLConnection]
      jarEntries <- Try:
          connection.getJarFile.stream.toScala(LazyList)
      entryNames =
        for
          entry <- jarEntries
          entryName = entry.getName
          if entryName.startsWith(s"$packageName/")
          baseEntryName = entryName.substring(packageName.length + 1)
        yield baseEntryName
    yield entryNames.toSet
  end getAllEntryNames

  def partitionFilesAndDirs(
      entryNames: Set[EntryName]
  ): (Set[Path], Set[Path]) =
    val (dirs, files) =
      entryNames.partition(e => e.isEmpty || e.endsWith("/"))

    val emptyDirs =
      dirs
        .map: dir =>
          (
            dir,
            entryNames.find(e => e != dir && e.startsWith(dir))
          )
        .filter((_, entry) => entry.isEmpty)
        .map((emptyDir, _) => emptyDir)

    val remainingDirs = dirs -- emptyDirs

    (files, remainingDirs)
  end partitionFilesAndDirs
end ClasspathLoader

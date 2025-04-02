package tikrana.web

import Types.*
import tikrana.util.Utils.*

import scala.collection.concurrent
import scala.util.{Failure, Success, Try}

// TODO Evict cache entries after some time-to-live
class WebCache(
    val loadResource: Path => Option[WebResource]
):

  case class Entry(resource: WebResource, payload: ByteArray)
  val cache = concurrent.TrieMap[Path, (Entry, Millis)]()

  def get(requestedPath: Path): Try[Option[ByteArray]] =
    // TODO Remove any leading slashes from path
    val path = requestedPath.stripPrefix("/")
    cache.get(path) match
      case None => getPayloadFor(path)
      case Some((Entry(resource, payload), time)) =>
        if !resource.stillExists() then
          cache.remove(path)
          val prefix = s"${path.stripSuffix("/")}/"
          cache --= cache.keys.filter(_.startsWith(prefix))
          Success(None)
        else if resource.lastModified() > time then
          getPayloadFor(path, resource).map(Some(_))
        else Success(Some(payload))

  private def getPayloadFor(path: Path): Try[Option[ByteArray]] =
    loadResource(path) match
      case Some(resource) =>
        for payload <- getPayloadFor(path, resource)
        yield Some(payload)
      case None =>
        Success(None)

  private def getPayloadFor(
      path: Path,
      resource: WebResource
  ): Try[ByteArray] =
    for
      payload <- resource.contents()
      _ = cache(path) = (
        Entry(resource, payload),
        resource.lastModified()
      )
    yield payload

end WebCache

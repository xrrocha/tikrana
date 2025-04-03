package tikrana.web

import Types.*
import tikrana.util.Utils.*
import tikrana.util.extension.OptionExtensions.swap


import scala.collection.concurrent
import scala.util.{Failure, Success, Try}

// TODO Evict cache entries after some time-to-live
class Cache(
    val loadResource: Path => Try[Option[Resource]]
):

  case class Entry(resource: Resource, payload: ByteArray)
  val cache = concurrent.TrieMap[Path, (Entry, Millis)]()

  def get(requestedPath: Path): Try[Option[ByteArray]] =
    val path = requestedPath.stripPrefix("/")
    cache.get(path) match
      case None => getPayloadFor(path)
      case Some((Entry(resource, payload), time)) =>
        if resource.hasVanished() then removeEntryFor(path)
        else if resource.hasChangedSince(time) then
          getPayloadFor(path, resource).map(Some(_))
        else Success(Some(payload))

  private def removeEntryFor(path: Path): Try[Option[ByteArray]] =
    cache -= path
    val prefix = s"${path.stripSuffix("/")}/"
    cache --= cache.keys.filter(_.startsWith(prefix))
    Success(None)

  private def getPayloadFor(path: Path): Try[Option[ByteArray]] =
    for 
      resourceOpt <- loadResource(path)
      resourceOptTry = 
        for 
          resource <- resourceOpt
        yield getPayloadFor(path, resource)
      resourceTryOpt <- resourceOptTry.swap
    yield resourceTryOpt

  private def getPayloadFor(
      path: Path,
      resource: Resource
  ): Try[ByteArray] =
    for
      payload <- resource.contents()
    yield payload
      .also: bytes =>
        cache(path) = (
          Entry(resource, bytes),
          resource.lastModified()
        )

end Cache

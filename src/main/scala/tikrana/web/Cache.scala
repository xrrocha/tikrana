package tikrana.web

import Types.*
import tikrana.util.Utils.*

import scala.collection.concurrent
import scala.util.{Failure, Success, Try}
import scala.annotation.tailrec

// TODO Evict cache entries after some time-to-live
class Cache(val loaders: Seq[ResourceLoader]):

  case class Entry(resource: Resource, payload: ByteArray)
  val cache = concurrent.TrieMap[Path, (Entry, Millis)]()

  def get(requestedPath: Path): Try[Option[ByteArray]] =
    val path = ResourceLoader.removeSlashes(requestedPath)
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
      optResource <- loadResource(path)
      optTryPayload =
        for resource <- optResource
        yield getPayloadFor(path, resource)
      optPayload <- optTryPayload.swap
    yield optPayload

  private def getPayloadFor(
      path: Path,
      resource: Resource
  ): Try[ByteArray] =
    for payload <- resource.contents()
    yield payload
      .also: bytes =>
        cache(path) = (
          Entry(resource, bytes),
          resource.lastModified()
        )

  private def loadResource(path: Path): Try[Option[Resource]] =
    @tailrec
    def scan(list: Seq[ResourceLoader]): Try[Option[Resource]] =
      list match
        case Nil => Success(None)
        case loader :: rest =>
          val result = loader.loadResource(path)
          result match
            case Failure(_) => result
            case Success(optResource) =>
              optResource match
                case Some(resource) => result
                case None           => scan(rest)
    end scan
    scan(loaders)
  end loadResource
end Cache

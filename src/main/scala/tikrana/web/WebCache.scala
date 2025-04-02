package tikrana.web

import Types.*

import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.WebResourceLoader.DefaultMimeType

import com.sun.net.httpserver.HttpExchange

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

// TODO Evict cache entries after some time-to-live
class WebCache(val config: HandlerConfig):

  case class Entry(resource: WebResource, payload: ByteArray)
  val cache = mutable.Map[Path, (Entry, Millis)]()

  def getPayloadFor(path: Path): Try[Option[ByteArray]] =
    cache.get(path) match
      case Some((Entry(resource, payload), time)) =>
        if !resource.stillExists() then
          // TODO Remove all cache entries descending from path
          cache.remove(path)
          Success(None)
        else if resource.hasChangedSince(time) then
          buildPayloadFor(path, resource)
            .map(Some(_))
        else Success(Some(payload))
      case None =>
        doGetPayloadFor(path)
  end getPayloadFor

  private def doGetPayloadFor(path: Path): Try[Option[ByteArray]] =
    val loadedResource =
      LazyList
        .from(config.loaders)
        .map(_.load(path))
        .find(_.isDefined)
        .flatten
    loadedResource match
      case Some(resource) =>
        buildPayloadFor(path, resource)
          .map(payload => Some(payload))
      case None =>
        Success(None)
  end doGetPayloadFor

  private def buildPayloadFor(
      path: Path,
      resource: WebResource
  ): Try[ByteArray] =
    resource
      .contents()
      .peek: payload =>
        cache(path) = (
          Entry(resource, payload),
          System.currentTimeMillis
        )
      .peekFailure: exc =>
        logger.logFine(exc, s"Error reading resource '$path'")
  end buildPayloadFor

end WebCache

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

class RootHttpHandler(config: HandlerConfig) extends HttpHandler:
  private val cache = Cache(
    loadResource = path =>
      LazyList
        .from(config.loaders)
        .map(_.load(path))
        // FIXME Excluding failures silently ignores errors (w/404)
        .filter(_.isSuccess)
        .map(_.get)
        .find(_.isDefined)
        .flatten
  )

  private val mimeTypes: Map[FileType, MimeType] =
    MimeTypes.DefaultMimeTypes ++ config.mimeTypes

  override def handle(exchange: HttpExchange): Unit =
    val path = getPath(exchange)

    val outcome =
      for
        cachedPayload <- cache.get(path)

        result = cachedPayload match
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
    getFileType(path)
      .flatMap(mimeTypes.get)
      .getOrElse(MimeTypes.DefaultMimeType)

  private[web] def getFileType(path: Path): Option[FileType] =
    path.extension.map(_.toLowerCase)
end RootHttpHandler

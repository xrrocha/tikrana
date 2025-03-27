package tikrana.web

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.Level.*
import java.util.logging.Logger
import scala.util.Try
import tikrana.util.Fault
import tikrana.util.Resources.*
import tikrana.util.Utils.*

protected val logger: Logger = Logger.getLogger("tikrana.web.WebServer")

object WebServer:
end WebServer

case class WebServer(config: Config):
  private val rootHandler = RootHandler(config)
  // FIXME Don't pass root handler this way
  private var webServer = createServer(rootHandler)

  def start(): Try[WebServer] =
    webServer
      .peek(_.start())
      .map(_ => this)

  def stop(): Try[WebServer] =
    webServer.peek(_.stop(config.stopDelay))
    webServer = createServer(rootHandler)
    webServer.map(_ => this)

  private def createServer(
      rootHandler: RootHandler
  ): Try[HttpServer] =
    Try:
      HttpServer.create(InetSocketAddress(config.address, config.port), 0)
    .peek: server =>
      // TODO Use config-provided executor
      server.setExecutor(null)
      server.createContext("/", rootHandler)
      logger.fine(s"Listening on ${config.uri}")
    .mapFailure: t =>
      Fault(s"Error creating web webServer", t)
        .logAsWarning(logger)
end WebServer

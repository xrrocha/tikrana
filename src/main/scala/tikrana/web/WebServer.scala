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

// TODO Support HTTPS
case class WebServer(config: Config):
  private val rootHandler = RootHandler(config)

  private var webServer = createServer()

  def start(): Try[WebServer] =
    webServer
      .peek(_.start())
      .peekFailure: t =>
        logger.warning(s"Web server is invalid: ${t.errorMessage}")
      .map(_ => this)

  def stop(): Try[WebServer] =
    webServer.peek(_.stop(config.stopDelay))
    webServer = createServer()
    webServer.map(_ => this)

  private def createServer(): Try[HttpServer] =
    Try:
      HttpServer.create(config.address, config.stopDelay)
    .peek: server =>
      // TODO Use config-provided executor
      server.setExecutor(null)
      server.createContext("/", rootHandler)
      logger.fine(s"Listening on ${config.uri}")
    .mapFailure: t =>
      Fault(s"Error creating web webServer: ${t.errorMessage}")
        .logAsWarning(logger)
end WebServer

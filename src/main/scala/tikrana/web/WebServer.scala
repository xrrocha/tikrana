package tikrana.web

import tikrana.util.Fault
import tikrana.util.Utils.*

import com.sun.net.httpserver.Filter
import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor
import java.util.logging.Level
import java.util.logging.Logger
import scala.util.{Failure, Success, Try}

import Types.*

protected val logger: Logger =
  Logger.getLogger("tikrana.web.WebServer")

// TODO Support HTTPS
class WebServer(config: ServerConfig):
  import config.*

  private var webServer: Option[HttpServer] = None
  private lazy val rootHandler = RootHttpHandler(handlerConfig)

  def start(): Try[WebServer] =
    logger.fine(s"Starting web server on $uri")
    for
      server <- createServer()
      _ <- Try(server.start())
    yield
      webServer = Some(server)
      this
  end start

  def stop(): Try[WebServer] =
    logger.fine(s"Stopping web server at $uri")
    for
      server <- webServer.toTry(Fault("Web server not started"))
      _ <- Try(server.stop(stopDelay))
    yield
      webServer = None
      this
  end stop

  private def createServer(): Try[HttpServer] =
    for server <- Try(
        HttpServer.create(
          address,
          backlog,
          "/",
          rootHandler
        )
      )
    yield server
      .also(_.setExecutor(newVirtualThreadPerTaskExecutor()))
end WebServer

object WebServer:
  // Minimal web server w/o custom configuration
  // http://localhost:1960
  @main
  def run() =
    for
      config <- ServerConfig()
      server <- WebServer(config).start()
    do
      logger.info(s"Web server running on ${config.uri}. Ctrl-C to shutdown...")
      // TODO Only `sys.runtime.addShutdownHook` actually works...
      sys.addShutdownHook:
          logger.info(s"Shutting down web server at ${config.uri}...")
          server.stop()
    end for

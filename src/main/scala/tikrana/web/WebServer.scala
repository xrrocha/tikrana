package tikrana.web

import com.sun.net.httpserver.{Filter, HttpServer}
import tikrana.util.Fault
import tikrana.util.Utils.*
import tikrana.web.Types.*

import java.net.InetSocketAddress
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor
import java.util.logging.{Level, Logger}
import scala.util.{Failure, Success, Try}

protected val logger: Logger =
  Logger.getLogger("tikrana.web.WebServer")

// TODO Support HTTPS
class WebServer(config: ServerConfig):
  private var webServer: Option[HttpServer] = None
  private lazy val rootHandler = RootHttpHandler(config.handlerConfig)

  def start(): Try[WebServer] =
    logger.fine(s"Starting web server on ${config.uri}")
    for
      server <- createServer()
      _ <- Try(server.start())
    yield
      webServer = Some(server)
      this
  end start

  def stop(): Try[WebServer] =
    logger.fine(s"Stopping web server at ${config.uri}")
    for
      server <- webServer.toTry(Fault("Web server not started"))
      _ <- Try(server.stop(config.stopDelay))
    yield
      webServer = None
      this
  end stop

  private def createServer(): Try[HttpServer] =
    for server <- Try(
        HttpServer.create(
          config.address,
          config.backlog,
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
    val outcome =
      for
        config <- ServerConfig()
        server <- WebServer(config).start()
      yield (config.uri, server)

    outcome match
      case Success((uri, server)) =>
        logger.info(s"Web server running on $uri. Ctrl-C to shutdown...")
        // TODO Only `sys.runtime.addShutdownHook` actually works...
        sys.addShutdownHook:
            logger.info(s"Shutting down web server at $uri...")
            server.stop()
      case Failure(err) =>
        logger.severe(s"Error starting web server: ${err.errorMessage}")
        sys.exit(1)

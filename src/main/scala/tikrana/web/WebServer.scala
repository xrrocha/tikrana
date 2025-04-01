package tikrana.web

import tikrana.util.Fault
import tikrana.util.Utils.*

import com.sun.net.httpserver.Filter
import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.Logger
import scala.util.{Failure, Success, Try}

import Types.*

protected val logger: Logger =
  Logger.getLogger("tikrana.web.WebServer")

// TODO Support HTTPS
class WebServer(config: Config):
  import config.*

  private var webServer: Option[HttpServer] = None
  private lazy val rootHandler = RootHandler(handlerConfig)

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
    // Post-Java 18 there's a better API for creating servers
    for server <- Try(HttpServer.create(address, backlog))
    yield
      // TODO Set up multithreaded executor
      server.setExecutor(null)
      server.createContext("/", rootHandler)
      server
end WebServer

object WebServer:
  @main
  def run() =
    for
      config <- Config()
      server <- WebServer(config).start()
    do
      logger.info(s"Web server running on ${config.uri}. Ctrl-C to shutdown...")
      sys.addShutdownHook:
          logger.info(s"Shutting down web server at ${config.uri}...")
          server.stop()
    end for

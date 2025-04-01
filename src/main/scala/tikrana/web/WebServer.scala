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
import sttp.client4.BackendOptions.ProxyType.Http

protected val logger: Logger =
  Logger.getLogger("tikrana.web.WebServer")

// TODO Support HTTPS
class WebServer(config: Config):
  import config.*

  // TODO Pass a handler config object, not the whole config
  private lazy val rootHandler = RootHandler(config)

  private var webServer: Option[HttpServer] = None

  def start(): Try[WebServer] =
    logger.fine(s"Starting web server on $uri")
    for
      server <- createServer()
      _ <- Try(server.start())
    yield
      webServer = Some(server)
      this
  end start

  def stop(): Try[Unit] =
    Try:
        for server <- webServer
        yield
          logger.fine(s"Stopping web server")
          server.stop(stopDelay)
          webServer = None
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

// TODO Add smart constructor to WebServer(config)
object WebServer:
  @main
  def run() =
    for
      config <- Config()
      server <- WebServer(config).start()
    do
      logger.info(s"Web server running on ${config.uri}. Ctrl-C to shutdown...")
      sys.addShutdownHook:
          logger.info("Shutting down...")
          server.stop()
    end for

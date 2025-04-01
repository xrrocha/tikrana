package tikrana.web

import Types.* 

import tikrana.util.Fault
import tikrana.util.Utils.*

import java.net.InetSocketAddress
import java.util.logging.{Level, Logger}
import com.sun.net.httpserver.HttpServer

import scala.util.Try

protected val logger: Logger = Logger.getLogger("tikrana.web.WebServer")

// TODO Add smart constructor to WebServer(config)
// TODO Support HTTPS
class WebServer(config: Config):
  import config.*

  private val rootHandler = RootHandler(config)

  private var webServer = createServer()

  def start(): Try[WebServer] =
    webServer
      .peek(_.start())
      .peekFailure: t =>
        logger.warning(s"Web server is invalid: ${t.errorMessage}")
      .map(_ => this)

  def stop(): Try[WebServer] =
    webServer.peek(_.stop(stopDelay))
    webServer = createServer()
    webServer.map(_ => this)

  private def createServer(): Try[HttpServer] =
    Try:
        HttpServer.create(address, stopDelay)
      .peek: server =>
        // TODO Use config-provided executor
        server.setExecutor(null)
        server.createContext("/", rootHandler)
        logger.fine(s"Listening on $uri")
      .mapFailure: t =>
        Fault(s"Error creating web webServer: ${t.errorMessage}")
          .logAsWarning(logger)
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
        logger.info("Shutting down...")
        server.stop()
    end for
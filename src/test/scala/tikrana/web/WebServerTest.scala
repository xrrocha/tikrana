package tikrana.web

import tikrana.util.Utils.*
import tikrana.util.Resources.*

import java.io.File
import java.net.URI
import scala.util.*

class WebServerTest extends munit.FunSuite:
  val packageName = "static"

  private val config = WebServerConfig(
    address = "localhost",
    // TODO Select random unassigned port
    // InetSocketAddress: A port number of zero will let the system
    // pick up an ephemeral port in a bind operation
    port = 1234,
    basePackages = Seq(packageName)
  )

  private val server = WebServer(config)

  private val indexPage = readResourceText(s"$packageName/index.html")
    .getOrElse(fail("Failed to read index page"))

  override def beforeEach(context: BeforeEach): Unit =
    server
      .start()
      .peekFailure(fault => fail(s"Failed to start server: $fault"))

  override def afterEach(context: AfterEach): Unit =
    server.stop()

  test("Web server servers implicit index page"):
    assertEquals(getPage(""), indexPage)

  test("Web server serves named resource"):
    assertEquals(getPage("index.html"), indexPage)

  def getPage(location: String): String =
    URI(s"${config.uri}/$location")
      .toURL
      .openStream()
      .let(_.use(_.readAllBytes()))
      .getOrElse(fail("Failed to read page"))
      .let(String(_, "UTF-8"))

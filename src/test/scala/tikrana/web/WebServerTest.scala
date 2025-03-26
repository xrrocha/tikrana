package tikrana.web

import java.net.URI
import scala.util.*
import tikrana.util.Utils.*

class WebServerTest extends munit.FunSuite:
  val config = WebServerConfig(
    address = "0.0.0.0",
    port = 1234
  )
  private val server = WebServer(config)

  override def beforeEach(context: BeforeEach): Unit =
    server.start()

  override def afterEach(context: AfterEach): Unit =
    server.stop()

  test("Web server servers basic content"):
    URI(config.uri)
      .toURL()
      .openStream()
      .let(Using(_)(_.readAllBytes()))
      .getOrElse(fail("Failed to read page"))
      .let(String(_, "UTF-8"))
      .also: result =>
        assertEquals(result, "<h1>In the works...</h1>")

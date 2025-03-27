package tikrana.web

import tikrana.util.Resources.*
import tikrana.util.Utils.*

import java.net.URI
import scala.util.{Failure, Success}

class WebServerTest extends munit.FunSuite:
  val packageName = "static/"

  private val config = Config(
    address = "localhost",
    // TODO Select random unassigned port
    // InetSocketAddress: A port number of zero will let the system
    // pick up an ephemeral port in a bind operation
    port = 1234,
    baseDirectory = None,
    basePackage = Some(packageName)
  ) match
    case Success(c) => c
    case Failure(t) =>
      val message =
        if t.getMessage != null then t.getMessage
        else t.toString
      fail(s"Bad web server configuration: $message")

  private val server = WebServer(config)

  private val indexPage = readResourceText(s"$packageName/index.html")
    .getOrElse(fail("Failed to read index page"))

  override def beforeEach(context: BeforeEach): Unit =
    server
      .start()
      .peekFailure(fault => fail(s"Failed to start server: $fault"))

  override def afterEach(context: AfterEach): Unit =
    server.stop()

  test("Web server servers directories from classpath".ignore):
    println("TODO: Web server servers directories from classpath")

  test("Web server servers implicit index page"):
    assertEquals(getPage(""), indexPage)

  test("Web server serves named resource"):
    assertEquals(getPage("index.html"), indexPage)

  test("Web server servers directory with no index file"):
    val expectedSubdir: String =
      """
      |<html>
      |<head>
      |  <meta charset='UTF-8'>
      |  <title>Directory listing</title>
      |</head>
      |<body>
      |  <h1>Directory listing</h1>
      |  <a href='other.html'>other.html</a>
      |</body>
      |</html>
      |""".stripMargin
    assertEquals(getPage("subdir"), expectedSubdir)

  def getPage(location: String): String =
    URI(s"${config.uri}/$location").toURL
      .openStream()
      .let(_.use(_.readAllBytes()))
      .getOrElse(fail("Failed to read page"))
      .let(String(_, "UTF-8"))

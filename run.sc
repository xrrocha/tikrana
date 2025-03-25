// Run with:
//     scala run project.scala src run.sc -- 0.0.0.0 1234

import java.awt.Desktop
import java.net.URI
import tikrana.util.Utils.*
import tikrana.web.WebServer

val address = "0.0.0.0"
val port = 1234

WebServer(address, port).let: webServer =>
  webServer.start()
  println(s"Web sever running on $address:$port. Ctrl-C to shutdown...")
  Runtime.getRuntime().addShutdownHook:
    Thread: () =>
      println("Shutting down...")
      webServer.stop()
  Desktop.getDesktop().browse(URI(s"http://localhost:$port/"))

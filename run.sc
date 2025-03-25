#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//> using file project.scala
//> using files src/main/scala

import java.awt.Desktop
import java.net.URI
import tikrana.util.Utils.*
import tikrana.web.WebServer

if args.length < 2 then 
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

val address = args(0)
val port = args(1).toInt

WebServer(address, port).let: webServer =>
  webServer.start()
  println(s"Web sever running on $address:$port. Ctrl-C to shutdown...")
  sys.runtime.addShutdownHook: // Should be sys.addShutdownHook?
    Thread: () =>
      println("Shutting down...")
      webServer.stop()

Desktop.getDesktop().browse(URI(s"http://localhost:$port/"))

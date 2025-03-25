#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//--sun-misc-unsafe-memory-access=allow

//> using file project.scala
//> using files src/main/scala

import java.awt.Desktop
import java.net.URI
import java.util.logging.Logger
import tikrana.util.Utils.*
import tikrana.web.WebServer

if args.length < 2 then 
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

val address = args(0)
val port = args(1).toInt

import WebServer.logger
WebServer(address, port).start()
  .peek: server =>
    logger.info(s"Web sever running on $address:$port. Ctrl-C to shutdown...")
    // TODO This shutdown hook doesn't appear to run. Java's Runtime does...
    sys.addShutdownHook:
      logger.info("Shutting down...")
      server.stop()
    // Desktop.getDesktop().browse(URI(s"http://localhost:$port/"))
  .peekLeft: fault =>
    fault.logSevere(logger, WITH_NO_STACK_TRACE)
    sys.exit(1)

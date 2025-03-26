#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//--sun-misc-unsafe-memory-access=allow

//> using file project.scala
//> using files src/main/scala

import tikrana.util.Utils.*
import tikrana.web.WebServer
import tikrana.web.WebServerConfig
import tikrana.web.WebServer.logger

if args.length < 2 then
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

val config = WebServerConfig(
  address = args(0),
  port = args(1).toInt,
  baseDirectory = System.getProperty("user.dir")
)

WebServer(config)
  .start()
  .peek: server =>
    logger.info(
      s"Web sever running on ${config.address}:${config.port}. Ctrl-C to shutdown..."
    )
    // TODO This shutdown hook doesn't appear to run. Java's Runtime does...
    sys.addShutdownHook:
      logger.info("Shutting down...")
      server.stop()
  .peekLeft: fault =>
    fault.logSevere(logger, WITH_NO_STACK_TRACE)
    sys.exit(1)

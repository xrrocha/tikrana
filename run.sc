#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//--sun-misc-unsafe-memory-access=allow

//> using file project.scala
//> using files src/main/scala

import java.util.logging.*
import tikrana.util.Utils.*
import tikrana.web.WebServer
import tikrana.web.WebServer.logger
import tikrana.web.WebServerConfig

if args.length < 2 then
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

// TODO i18n w/resource bundlers
"logging.properties"
  .let: loggingResource =>
    getResourceAsStream(loggingResource) match
      case Some(is) =>
        LogManager.getLogManager().readConfiguration(is)
      case None =>
        println(s"WARNING: No '$loggingResource' found, using logging defaults")

val config = WebServerConfig(
  address = args(0),
  port = args(1).toInt
)
logger.config(s"Configuration: $config")

WebServer(config)
  .start()
  .peek: server =>
    logger.info(
      s"Web sever running on ${config.uri}. Ctrl-C to shutdown..."
    )
    // TODO This shutdown hook doesn't appear to run. Java's Runtime does...
    sys.addShutdownHook:
      logger.info("Shutting down...")
      server.stop()
  .peekLeft: fault =>
    fault.logSevere(logger, WITH_NO_STACK_TRACE)
    sys.exit(1)

#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//--sun-misc-unsafe-memory-access=allow

//> using file project.scala
//> using files src/main/scala

import tikrana.util.Utils.*
import tikrana.util.Resources.*
import tikrana.web.{Config, WebServer}

import java.util.logging.*

if args.length < 2 then
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

val outcome =
  for
    is <- openResource("logging.properties")
    _ <- is.use(LogManager.getLogManager.readConfiguration(_))
    logger = Logger.getLogger("tikrana.web.run")
    config <- Config(
      address = args(0),
      port = args(1).toInt,
      baseDirectory = Some(System.getProperty("user.dir"))
    )
    webServer <- WebServer(config).start()
    _ = logger.info(
      s"Web sever running on ${config.uri}. Ctrl-C to shutdown..."
    )
    _ = sys.addShutdownHook:
        logger.info("Shutting down...")
        webServer.stop()
  yield ()

outcome.peekFailure: t =>
  println(s"Error: ${t.errorMessage}")
  sys.exit(1)
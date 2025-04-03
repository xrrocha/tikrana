#!/usr/bin/env -S scala shebang --suppress-directives-in-multiple-files-warning

//--sun-misc-unsafe-memory-access=allow

// -XX:+UseCompactObjectHeaders

//> using file project.scala
//> using files src/main/scala

import tikrana.util.Resources.*
import tikrana.util.Utils.*
import tikrana.web.{ServerConfig, WebServer}

import java.util.logging.*

if args.length < 2 then
  println("Usage: run.sc netAddress netPort")
  sys.exit(1)

val outcome =
  for
    _ <- LogManager.getLogManager.readConfiguration("logging.properties")
    config <- ServerConfig(
      address = args(0),
      port = args(1).toInt,
      baseDirectory = Some(System.getProperty("user.dir"))
    )
    webServer <- WebServer(config).start()
  yield
    val logger = Logger.getLogger("tikrana.web.run")
    logger.info(s"Web sever running on ${config.uri}. Ctrl-C to shutdown...")
    sys.addShutdownHook:
        logger.info("Shutting down...")
        webServer.stop()

outcome.peekFailure: t =>
    println(s"Error: ${t.errorMessage}")
    sys.exit(1)

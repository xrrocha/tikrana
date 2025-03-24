//> using scala 3.6.4
//> using platform jvm
//> using options -deprecation -release:24

//> using jvm graalvm-oracle:24
//> using javaOpt -Xmx4g

//> using resourceDir src/main/resources
//> using test.resourceDir src/test/resources

//> using toolkit 0.7.0
//> using test.toolkit 0.7.0
//> using dep org.scala-lang::scala3-compiler::3.6.4

//> using test.dep org.scalameta::munit::1.1.0
//> using test.dep com.h2database:h2:2.3.232
//> using test.dep org.apache.commons:commons-dbcp2:2.13.0

// //> using testFramework "munit.Framework"
// //> using options "-coverage-out:${.}"

import java.awt.Desktop
import java.net.URI
import tikrana.util.Utils.*
import tikrana.web.WebServer

// Run with:
//     scala run project.scala src -- 0.0.0.0 1234
object Runner:
  @main
  def run(address: NetAddress, port: Port) =
    WebServer(address, port).let: webServer =>
      webServer.start()
      println(s"Web sever running on $address:$port. Ctrl-C to shutdown...")
      Runtime.getRuntime().addShutdownHook:
        Thread: () =>
          println("Shutting down...")
          webServer.stop()
      Desktop.getDesktop().browse(URI(s"http://localhost:$port/"))

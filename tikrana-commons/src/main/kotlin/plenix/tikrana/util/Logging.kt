package plenix.tikrana.util

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.logging.LogManager

fun initLogger() {
    initLogger("logging.properties")
}

fun initLogger(resourceName: String) {
    openResource(resourceName)
        .map(LogManager.getLogManager()::readConfiguration)
        .getOrElse { throw IllegalStateException("Can't find resource $resourceName") }
}

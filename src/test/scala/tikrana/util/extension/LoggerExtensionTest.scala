package tikrana.util.extension 

import LoggerExtensions.* 
import java.util.logging.{Level, Logger}

class LoggerExtensionTest extends munit.FunSuite:
    test("`logt` logs lazily"):
        ()
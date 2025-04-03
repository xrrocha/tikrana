package tikrana.util.extension

import tikrana.util.extension.OptionExtensions.*

import scala.util.{Failure, Success, Try}

class OptionExtensionTest extends munit.FunSuite:
  test("Converts to Success"):
      val option = Some("yeah!")
      assertEquals(
        option.toTry(Exception("Can't happen")),
        Success("yeah!")
      )
  test("Converts to Failure"):
      val option = None
      val exc = Exception("Must happen")
      assertEquals(
        option.toTry(exc),
        Failure(exc)
      )

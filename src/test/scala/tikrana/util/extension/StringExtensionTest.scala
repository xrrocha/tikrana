package tikrana.util.extension

import tikrana.util.extension.StringExtensions.*

class StringExtensionTest extends munit.FunSuite:
  test("Determines extension"):
      val filename = "filename.txt"
      val extension = filename.extension
      assert(extension.isDefined)
      assertEquals(filename.extension.get, "txt")
  test("Fails on no extension"):
      val filename = "filename"
      val extension = filename.extension
      assert(extension.isEmpty)

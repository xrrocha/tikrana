package tikrana.util.extension

import tikrana.util.extension.ThrowableExtensions.*

class ThrowableExtensionTest extends munit.FunSuite:
  test("errorMessage returns the message of the Throwable"):
      val exception = new Exception("Test exception")
      assert(exception.errorMessage == "Test exception")
  test("errorMessage returns Throwable class name if message is null"):
      val exception = new Exception()
      assert(exception.errorMessage == "java.lang.Exception")

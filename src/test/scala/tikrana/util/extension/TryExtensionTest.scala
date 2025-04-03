package tikrana.util.extension

import tikrana.util.extension.TryExtensions.*

import scala.util.{Failure, Success, Try}

class TryExtensionTest extends munit.FunSuite:
  test("peek() calls side-effecting function on success"):
      var changed = false
      Success(0)
        .peek: value =>
          assert(value == 0)
          changed = true
        .foreach(_ => assert(changed))
  test("peekFailure calls side-effecting function on failure"):
      var changed = false
      Failure(Exception("Test exception"))
        .peekFailure: exc =>
          assert(exc.getMessage == "Test exception")
          changed = true
        .getOrElse(assert(changed))
  test("mapFailure replaces exception on failure"):
      Failure(Exception("Test exception 1"))
        .mapFailure: exc =>
          assert(exc.getMessage == "Test exception 1")
          Exception("Test exception 2", exc)
        .peekFailure: exc =>
          assert(exc.getMessage == "Test exception 2")
          assert(exc.getCause != null)
          assert(exc.getCause.getMessage == "Test exception 1")

package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

class TryTest extends munit.FunSuite:
  test("mapFailure works"):
    val result =
      Try(throw Exception("Kaboom!"))
        .mapFailure: exc =>
          Fault("Kaput!", exc)

    assert(result.isFailure)

    val throwable = result.fold(identity, _ => fail("Can't be right"))
    assert(throwable.isInstanceOf[Fault])
    val fault = throwable.asInstanceOf[Fault]

    assertEquals(fault.message, "Kaput!")
    assert(fault.cause != null)
    assertEquals(fault.cause.getMessage, "Kaboom!")

  test("peek works"):
    var i = 0
    val result =
      Try(42)
        .peek: v =>
          assertEquals(v, 42)
          i = 1
    assert(result.isSuccess)
    assert(i == 1)
    assertEquals(result.get, 42)

  test("peekLeft works"):
    var i = 0
    val result =
      Try(throw Exception("Kaboom!"))
        .peekFailure: exc =>
          assertEquals(exc.getMessage, "Kaboom!")
          i = 1
    assert(result.isFailure)
    assert(i == 1)
    assertEquals(result.getOrElse(42), 42)
end TryTest

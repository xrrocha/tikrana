package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

class FaultTest extends munit.FunSuite:
  test("Catches throwing"):
    val result = Fault.catching(throw Exception("Kaboom!"))
    assert(result.isLeft)
    val throwable = result.fold(identity, _ => fail("Can't be right!"))
    assertEquals(throwable.getMessage, "Kaboom!")

  test("Passes not throwing"):
    Fault.catching(42)
      .also: either =>
        assert(either.isRight)
        val value = either.getOrElse(fail("Can't be left"))
        assertEquals(value, 42)

  test("mapLeft works"):
    val result =
      Try(throw Exception("Kaboom!")).toEither
        .mapLeft: exc =>
          Fault("Kaput!", exc, "answer" -> "42")

    assert(result.isLeft)

    val fault = result.fold(identity, _ => fail("Can't be right"))
    assertEquals(fault.message, "Kaput!")
    assertEquals(fault.extraInfo, Seq(("answer", "42")))

    val cause = fault.cause
    assert(cause.isDefined)
    assert(cause.get.isInstanceOf[Exception])

    val exc = cause.get.asInstanceOf[Exception]
    assertEquals(exc.getMessage, "Kaboom!")

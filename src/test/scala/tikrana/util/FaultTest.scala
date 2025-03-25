package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

// TODO Test [recursive] logging
class FaultTest extends munit.FunSuite:
  test("Catches throwing"):
    val either = Fault.catching(throw Exception("Kaboom!"))
    assert(either.isLeft)
    val throwable = either.fold(identity, _ => fail("Can't be right!"))
    assertEquals(throwable.getMessage, "Kaboom!")

  test("Passes not throwing"):
    Fault.catching(42)
      .also: either =>
        assert(either.isRight)
        val value = either.getOrElse(fail("Can't be left"))
        assertEquals(value, 42)

  test("mapLeft works"):
    val either =
      Try(throw Exception("Kaboom!")).toEither
        .mapLeft: exc =>
          Fault("Kaput!", exc)

    assert(either.isLeft)

    val fault = either.fold(identity, _ => fail("Can't be right"))
    assertEquals(fault.message, "Kaput!")

    val cause = fault.cause
    assert(cause.isDefined)
    assert(cause.get.isInstanceOf[Exception])

    val exc = cause.get.asInstanceOf[Exception]
    assertEquals(exc.getMessage, "Kaboom!")

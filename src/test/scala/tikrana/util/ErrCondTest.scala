package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

class ErrCondTest extends munit.FunSuite:
  test("mapLeft works"):
    val result =
      Try(throw Exception("Kaboom!")).toEither
        .mapLeft: exc =>
          ErrCond("Kaput!", exc, "answer" -> "42")

    assert(result.isLeft)

    val errCond = result.fold(identity, _ => fail("Can't be right"))
    assertEquals(errCond.message, "Kaput!")
    assertEquals(errCond.extraInfo, Seq(("answer", "42")))

    val cause = errCond.cause
    assert(cause.isDefined)
    assert(cause.get.isInstanceOf[Exception])

    val exc = cause.get.asInstanceOf[Exception]
    assertEquals(exc.getMessage, "Kaboom!")

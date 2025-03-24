package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

class ErrCondTest extends munit.FunSuite:
  test("mapLeft works"):
    val result =
      Try(throw Exception("Kaboom!")).toEither
        .mapLeft: exc =>
          ErrCond("Kaput!", exc, "answer" -> "42")
    result match
      case Right(_) =>
        fail("Can't be right!")
      case Left(errCond) =>
        assertEquals(errCond.message, "Kaput!")
        assertEquals(errCond.extraInfo.size, 1)
        assertEquals(errCond.extraInfo(0), ("answer", "42"))
        errCond.cause match
          case None =>
            fail("Gotta have cause!")
          case Some(cause) =>
            cause match
              case t: Throwable =>
                assertEquals(t.getMessage, "Kaboom!")
              case _ =>
                fail("Gotta be throwable!")

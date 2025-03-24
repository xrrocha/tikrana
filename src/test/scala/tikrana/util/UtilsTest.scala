package tikrana.util

import tikrana.util.Utils.*

class UtilsTest extends munit.FunSuite:

  test("Computes execution time"):
    val waitTime = 1000L
    val (_, elapsedTime) = time(Thread.sleep(waitTime))
    assert(elapsedTime - waitTime <= 10L)

  test("KLike's 'let' and 'also' work as advertised"):
    var i = 0
    val x: Either[String, Int] = Right(0)

    val result =
      (i + 2)
        .let: r =>
          r * 3
        .also: r =>
          assert(r == 6)
          i = 5

    assert(i == 5)
    assert(result == 6)

package tikrana.util

import scala.util.*
import tikrana.util.Utils.*

class ResourcesTest extends munit.FunSuite:
  test("Reads existing resource"):
    val result = readResourceText("resource.txt")
    assert(result.isRight)
    result.foreach: r =>
      assertEquals(r.trim, "I'm a resource")
  test("Fails on reading non-existing resource"):
    val result = readResourceText("nonexistent.txt")
    val cause = result.fold(_.cause, _ => fail("Can't be right!"))
    assert(cause.get.isInstanceOf[java.io.FileNotFoundException])
  test("Gets existing resource"):
    assert(getResource("resource.txt").isDefined)
    assert(getResource("non-existent.txt").isEmpty)

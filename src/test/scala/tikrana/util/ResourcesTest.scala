package tikrana.util

import tikrana.util.Resources.*

class ResourcesTest extends munit.FunSuite:
  test("Reads existing resource"):
      val result = readResourceText("resource.txt")
      assert(result.isSuccess)
      result.foreach: r =>
          assertEquals(r.trim, "I'm a resource")

  test("Fails on reading non-existing resource"):
      val result = readResourceText("nonexistent.txt")
      val cause = result.fold(identity, _ => fail("Can't be right!"))
      assert(cause.isInstanceOf[java.io.FileNotFoundException])

  test("Gets existing resource"):
      assert(getResource("resource.txt").isDefined)
      assert(getResource("non-existent.txt").isEmpty)

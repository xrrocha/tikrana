package tikrana.util

import tikrana.util.Utils.*

class IOTest extends munit.FunSuite:
  test("Closeable works"):
      import java.io.Closeable

      var closed = false

      object myCloseable extends Closeable:
        def close(): Unit =
          closed = true

      val result =
        myCloseable.use: _ =>
            assert(!closed)
            42

      assert(closed)
      assert(result.isSuccess)

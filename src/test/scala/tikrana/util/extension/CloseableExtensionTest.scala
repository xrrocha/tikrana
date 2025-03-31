package tikrana.util.extension

import java.io.Closeable
import CloseableExtensions.*

class CloseableExtensionTest extends munit.FunSuite:
    test("Closes after `use` execution"):
      var changed = false
      val closeable: Closeable = () =>
        changed = true
      assert(!changed)
      closeable.use: c =>
        ()
      assert(changed)
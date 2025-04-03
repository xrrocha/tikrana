package tikrana.util.extension

import java.io.Closeable
import scala.util.{Try, Using}

object CloseableExtensions:
  extension [A <: Closeable](closeable: A)
    def use[B](f: A => B): Try[B] =
      Using(closeable)(f)

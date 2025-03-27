package tikrana.util

object Utils:
  export Extensions.*
  export Types.*

  implicit class KLike[T](t: T):
    def let[R](f: T => R): R = f(t)
    def also(f: T => Unit): T = { f(t); t }
  end KLike

  def time[A](action: => A): (A, Long) =
    val startTime = System.currentTimeMillis()
    (action, System.currentTimeMillis() - startTime)

  def ctxClassLoader: ClassLoader =
    Thread.currentThread().getContextClassLoader
end Utils

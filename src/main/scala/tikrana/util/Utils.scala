package tikrana.util

import scala.util.{Failure, Success, Try, Using}

object Utils:
  export ErrCond.*
  export IO.*
  export Resources.*

  implicit class KLike[T](t: T):
    def let[R](f: T => R): R = f(t)
    def also(f: T => Unit): T = { f(t); t }
  end KLike

  def time[A](action: => A): (A, Long) =
    val startTime = System.currentTimeMillis()
    (action, System.currentTimeMillis() - startTime)

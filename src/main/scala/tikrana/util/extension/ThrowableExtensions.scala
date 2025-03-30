package tikrana.util.extension

object ThrowableExtensions:
  extension (throwable: Throwable)
    def errorMessage: String =
      if throwable.getMessage != null then throwable.getMessage
      else throwable.toString

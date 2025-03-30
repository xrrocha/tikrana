package tikrana.util.extension

import tikrana.util.Types.Extension

object StringExtensions:
  extension (string: String)
    def extension: Option[Extension] =
      val pos = string.lastIndexOf('.')
      if pos < 0 then None
      else Some(string.substring(pos + 1))

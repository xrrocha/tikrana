package tikrana.web

object Types:
  // FIXME Path may be a bad choice: it clashes w/java.nio.file.Path`
  type Path = String

  type MimeType = String
  type FileType = String
  type HeaderName = String
  type HeaderValue = String

package tikrana.web

import tikrana.util.Utils.*
import tikrana.web.Types.*

enum HttpCode(val code: Int, val message: String):
  case OK extends HttpCode(200, "OK")
  case BAD_REQUEST extends HttpCode(400, "Bad Request")
  case UNAUTHORIZED extends HttpCode(401, "Not Authorized")
  case NOT_FOUND extends HttpCode(404, "Not Found")
  case INTERNAL_SERVER_ERROR extends HttpCode(500, "Internal Server Error")
end HttpCode

case class Result(
    httpCode: HttpCode,
    contents: ByteArray,
    mimeType: MimeType = MimeTypes.DefaultMimeType,
    headers: Map[HeaderName, Seq[HeaderValue]] = Map.empty
)
object Result:
  val NotFound: Result = Result(
    HttpCode.NOT_FOUND,
    "<h1>Not found</h1>".getBytes(),
    "text/html"
  )

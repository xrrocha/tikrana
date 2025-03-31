package tikrana.util.extension

import InputStreamExtensions.* 

import java.io.{ByteArrayInputStream, InputStream}

class InputStreamExtensionTest extends munit.FunSuite:
  test("Reads bytes"):
    val bytes = "Now is the time for all good men"
        .getBytes("UTF-8")
    val inputStream = ByteArrayInputStream(bytes)
    val result = inputStream.readBytes()
    assert(result.isSuccess)
    assertEquals(
     String(bytes, "UTF-8"),
     String(result.get, "UTF-8")
    )
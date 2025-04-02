package tikrana.web

import tikrana.util.Types.{ByteArray, Millis}

import scala.util.Success
import ujson.ByteArrayParser

class WebCacheTest extends munit.FunSuite:
  test("Retrieves existing resource computing payload only once"):
      var computed = 0
      val payload = Array[Byte]()
      val expectedResult = Some(payload)

      val cache = WebCache(
        loadResource = _ =>
          Some(
            new WebResource:
              override def contents() =
                computed += 1
                Success(payload)
              override def stillExists() = true
              override def hasChangedSince(time: Millis) = false
          )
      )

      val result1 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 1"))
      assertEquals(result1, expectedResult)
      assertEquals(computed, 1)

      val result2 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 2"))
      assertEquals(result2, expectedResult)
      assertEquals(computed, 1)

  test("Returns None on non-existing resource without caching"):
      var computed = 0
      val payload = Array[Byte]()
      val expectedResult: Option[ByteArray] = None

      val cache = WebCache(_ => { computed += 1; None })

      val result1 = cache
        .get("non-existing")
        .getOrElse(fail("Failed to get resource on pass 2"))
      assertEquals(result1, None)
      assertEquals(computed, 1)

      val result2 = cache
        .get("non-existing")
        .getOrElse(fail("Failed to get resource on pass 2"))
      assertEquals(result2, None)
      assertEquals(computed, 2)

  test("Retrieves existing resource re-computing payload on change"):
      var computed = 0
      var payload = "Pass #1".getBytes()
      def expectedResult = Some(payload)

      val cache = WebCache(
        loadResource = _ =>
          var previousPayload = payload
          Some(
            new WebResource:
              override def contents() =
                computed += 1
                Success(payload)
              override def stillExists() = true
              override def hasChangedSince(time: Millis) =
                println(s"$payload != $previousPayload: ${payload != previousPayload}") 
                if payload == previousPayload then false
                else
                  previousPayload = payload
                  true
          )
      )

      val result1 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 1"))
      assertEquals(result1, expectedResult)
      assertEquals(computed, 1)

      val result2 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 2"))
      assertEquals(result2, expectedResult)
      assertEquals(computed, 1)

      payload = "Pass #2".getBytes()

      val result3 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 3"))
      assertEquals(result3, expectedResult)
      assertEquals(computed, 2)

      val result4 =
        cache
          .get("whatever")
          .getOrElse(fail("Failed to get resource on pass 4"))
      assertEquals(result4, expectedResult)
      assertEquals(computed, 2)

  test("Provides consistent results on multi-threaded access"):
    ()
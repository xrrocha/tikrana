package tikrana.web

import tikrana.util.Types.{ByteArray, Millis}
import tikrana.web.Types.Path

import scala.collection.mutable
import scala.util.{Success, Try}

class CacheTest extends munit.FunSuite:
  test("Retrieves existing resource computing payload only once"):
      var computed = 0
      val payload = "Some payload"
      val payloadTime = System.currentTimeMillis

      val loader = new ResourceLoader:
        override def loadResource(path: Path): Try[Option[Resource]] =
          Try:
              Some:
                  new Resource:
                    override def contents() =
                      computed += 1
                      Success(payload.getBytes)
                    override def stillExists() =
                      true
                    override def lastModified(): Millis =
                      payloadTime
      val cache = Cache(Seq(loader))

      verifyCacheEntry(cache, "path", payload)
      assertEquals(computed, 1)

      verifyCacheEntry(cache, "path", payload)
      assertEquals(computed, 1)

  test("Returns None on non-existing resource without caching"):
      var computed = 0
      val payload = "Some payload"

      val loader = new ResourceLoader:
        override def loadResource(path: Path): Try[Option[Resource]] =
          computed += 1
          Success(None)
      val cache = Cache(Seq(loader))

      verifyCacheEntry(cache, "non-existing")
      assertEquals(computed, 1)

      verifyCacheEntry(cache, "non-existing")
      assertEquals(computed, 2)

  test("Retrieves existing resource re-computing payload on change"):
      var computed = 0
      var payload = "Pass #1"
      var payloadTime = System.currentTimeMillis

      val loader = new ResourceLoader:
        override def loadResource(path: Path): Try[Option[Resource]] =
          var previousPayload = payload
          Try:
              Some:
                  new Resource:
                    override def contents() =
                      computed += 1
                      Success(payload.getBytes)
                    override def stillExists() = true
                    override def lastModified(): Millis =
                      payloadTime
      val cache = Cache(Seq(loader))

      verifyCacheEntry(cache, "path", "Pass #1")
      assertEquals(computed, 1)

      payload = "Pass #2"
      payloadTime = System.currentTimeMillis

      verifyCacheEntry(cache, "path", "Pass #2")
      assertEquals(computed, 2)

  test("Removes cache entry on resource vanishing"):
      var computed = 0
      val repo = mutable.Map[Path, (String, Millis)](
        "path" -> ("path #1", System.currentTimeMillis),
        "path/subpath1" -> ("subpath #1.1", System.currentTimeMillis),
        "path/subpath2" -> ("subpath #1.2", System.currentTimeMillis)
      )

      val loader = new ResourceLoader:
        override def loadResource(path: Path): Try[Option[Resource]] =
          Try:
              for _ <- repo.get(path)
              yield new Resource:
                override def contents(): Try[ByteArray] =
                  computed += 1
                  Success(repo(path)._1.getBytes)
                override def stillExists() =
                  repo.contains(path)
                override def lastModified(): Millis =
                  repo
                    .get(path)
                    .map(_._2)
                    .getOrElse(System.currentTimeMillis)
      val cache = Cache(Seq(loader))

      verifyCacheEntry(cache, "path", "path #1")
      assertEquals(computed, 1)

      repo("path") = ("path #2", System.currentTimeMillis)
      verifyCacheEntry(cache, "path", "path #2")
      assertEquals(computed, 2)

      verifyCacheEntry(cache, "path/subpath1", "subpath #1.1")
      assertEquals(computed, 3)
      verifyCacheEntry(cache, "path/subpath2", "subpath #1.2")
      assertEquals(computed, 4)

      repo --= Seq("path", "path/subpath1", "path/subpath2")
      verifyCacheEntry(cache, "path")
      verifyCacheEntry(cache, "path/subpath1")
      verifyCacheEntry(cache, "path/subpath2")
      assertEquals(computed, 4)

  test("Provides consistent results on multi-threaded access"):
      ()

  def verifyCacheEntry(cache: Cache, path: Path) =
    val result =
      cache
        .get(path)
        .getOrElse(fail(s"Failed to get resource '$path'"))
    assertEquals(result.map(String(_)), None)

  def verifyCacheEntry(cache: Cache, path: Path, payload: String) =
    val result =
      cache
        .get(path)
        .getOrElse(fail(s"Failed to get resource '$path'"))
    assertEquals(result.map(String(_)), Some(payload))

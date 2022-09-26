package plenix.tikrana.webserver

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import java.io.InputStream
import java.io.OutputStream

// NOTE Not passing Codec generic type info is deliberate! Payloads are assumed to contain runtime type info
interface Codec {
    fun decode(inputStream: InputStream): Any?
    fun encode(data: Any?, outputStream: OutputStream): Unit?
}

open class JacksonCode(private val objectMapper: ObjectMapper) : Codec {
    override fun decode(inputStream: InputStream): Any? =
        objectMapper.readValue(inputStream, Any::class.java)

    override fun encode(data: Any?, outputStream: OutputStream) =
        data?.let { objectMapper.writeValue(outputStream, it) }
}

open class HttpCodec(private val codecs: Map<ContentType, Codec>) {

    init {
        codecs.keys.filterNot(ContentTypes::isValid).let { invalidContentTypes ->
            require(invalidContentTypes.isEmpty()) {
                "Invalid content types: ${invalidContentTypes.joinToString(", ", "[", "]")}}"
            }
        }
    }

    fun <T> decode(inputStream: InputStream, contentType: ContentType): Either<Failure, T?> =
        ContentTypes.validate(contentType)
            .flatMap { cType ->
                Either.fromNullable(codecs[cType])
                    .mapLeft { ApplicationFailure("No codec for content type $cType") }
            }
            .flatMap { codec ->
                Either.catch { codec.decode(inputStream) }
                    .mapLeft { ApplicationFailure("Error decoding input stream", it) }
                    .flatMap { decodedValue ->
                        @Suppress("UNCHECKED_CAST")
                        Either.catch { decodedValue as T }
                            .mapLeft {
                                val fromClass = decodedValue?.let { decodedValue::class.qualifiedName }
                                ApplicationFailure("Error converting from $fromClass}", it)
                            }
                    }
            }

    fun encode(data: Any?, outputStream: OutputStream, contentType: String): Either<Failure, Unit?> =
        Either.fromNullable(codecs[contentType])
            .mapLeft { ApplicationFailure("No codec for content type $contentType") }
            .flatMap { codec ->
                Either.catch { codec.encode(data, outputStream) }
                    .mapLeft { ApplicationFailure("Error encoding to output stream", it) }
            }
}

object SimpleHttpCodec : HttpCodec(
    mapOf(
        "application/json" to JacksonCode(ObjectMapper().registerKotlinModule()),
        "application/yaml" to JacksonCode(YAMLMapper.builder().build().registerKotlinModule()),
    )
)
package org.japancv.sample.data.fr

import android.annotation.SuppressLint
import android.graphics.Bitmap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.japancv.sample.data.*
import org.japancv.sample.ui.ktx.toBase64
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private const val FACE_MODEL = ""
private const val CLASS = ""
private const val TTL = 3_600L
private const val MAX_ENTITY_NUM = 10
private const val THRESHOLD = 0.9
private const val COLLECTION = ""
private const val KEY = ""
private const val BASE_URL = ""
private const val INSTANCE_ID = ""
private const val API_KEY = ""

fun faceRecognitionApi(): FaceRecognitionApi = AnyseeFaceRecognitionApi(getKtorClient())

fun generateID(size: Int): String {
    val source = "A1BCDEF4G0H8IJKLM7NOPQ3RST9UVWX52YZab1cd60ef2ghij3klmn49opq5rst6uvw7xyz8"
    return (source).map { it }.shuffled().subList(0, size).joinToString("")
}

fun getKtorClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Timber.i("Logger Ktor => %s", message)
                }

            }
            level = LogLevel.ALL
        }

        install(DefaultRequest) {
            url(BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("instance-id", INSTANCE_ID)
            header("api-key", API_KEY)
        }

        engine {
            config {
                val trustAllCerts = arrayOf(
                    @SuppressLint("CustomX509TrustManager") object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                })

                val sslContext: SSLContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())

                sslSocketFactory(sslContext.socketFactory, trustAllCerts.first())
                hostnameVerifier { _, _ -> true }
            }
        }

        expectSuccess = true
    }
}

private class AnyseeFaceRecognitionApi(private val client: HttpClient) : FaceRecognitionApi {

    override suspend fun create(bitmap: Bitmap, name: String?): Result<String> {
        val faceInBase64: String = bitmap.toBase64()
        val image = Image(true, Details(), faceInBase64)
        val request = CreateRequest(
            FACE_MODEL,
            CLASS,
            COLLECTION,
            KEY,
            TTL,
            listOf(Metadata(
                name = "name",
                type = "string",
                value = name ?: generateID(10)
            )),
            image
        )
        return try {
            val entity: Entity = client.post(ENDPOINT_CREATE){
                setBody(request)
            }.body()
            Result.success(entity.UUID)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun search(bitmap: Bitmap): Result<List<String>> {
        val faceInBase64: String = bitmap.toBase64()
        val image = Image(true, Details(), faceInBase64)
        val request = SearchRequest(FACE_MODEL, MAX_ENTITY_NUM, THRESHOLD, COLLECTION, image)
        return try {
            val searchResult: SearchResponse = client.post(ENDPOINT_SEARCH) {
                setBody(request)
            }.body()
            val users = mutableListOf<String>()
            searchResult.entitiesFound.forEach { entity ->
                users.add(entity.UUID)
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val ENDPOINT_CREATE = "/api/v1/entities/faces"
        const val ENDPOINT_SEARCH = "/api/v1/entities/faces/search"
    }
}

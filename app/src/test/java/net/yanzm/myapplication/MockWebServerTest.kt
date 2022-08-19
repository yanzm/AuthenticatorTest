package net.yanzm.myapplication

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verify
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.yanzm.myapplication.data.ApiAdapter
import net.yanzm.myapplication.data.MyAuthenticator
import net.yanzm.myapplication.data.TokenProvider
import net.yanzm.myapplication.data.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import retrofit2.Retrofit

class MockWebServerTest {

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
    @Test
    fun test() = runTest {
        val server = MockWebServer()

        val dispatcher: Dispatcher = object : Dispatcher() {
            @Throws(InterruptedException::class)
            override fun dispatch(request: RecordedRequest): MockResponse {
                val token = request.headers["Authorization"]
                if (token != "Bearer valid_token") {
                    return MockResponse().setResponseCode(401)
                }

                if (request.path == "/user") {
                    return MockResponse().setResponseCode(200)
                        .setBody("""{ "id": "Android" }""")
                }

                return MockResponse().setResponseCode(404)
            }
        }
        server.dispatcher = dispatcher

        val tokenProvider = spyk(MockTokenProvider())

        val client = OkHttpClient.Builder()
            .authenticator(MyAuthenticator(tokenProvider))
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .addHeader("Authorization", "Bearer invalid_token")

                chain.proceed(requestBuilder.build())
            }
            .build()

        val contentType = "application/json".toMediaType()
        val apiAdapter = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(Json.asConverterFactory(contentType))
            .build()
            .create(ApiAdapter::class.java)

        val job1 = launch(UnconfinedTestDispatcher()) {
            val user = apiAdapter.getUser()
            assertEquals(User("Android"), user)
        }

        val job2 = launch(UnconfinedTestDispatcher()) {
            val user = apiAdapter.getUser()
            assertEquals(User("Android"), user)
        }

        job1.join()
        job2.join()

        server.shutdown()

        verify(exactly = 2) { tokenProvider.token() }
        verify { tokenProvider.refreshToken() }
        confirmVerified(tokenProvider)
    }


    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
    @Test
    fun test2() = runTest {
        val server = MockWebServer()

        val dispatcher: Dispatcher = object : Dispatcher() {
            @Throws(InterruptedException::class)
            override fun dispatch(request: RecordedRequest): MockResponse {
                val token = request.headers["Authorization"]
                if (token != "Bearer valid_token") {
                    if (request.path == "/user1") {
                        Thread.sleep(100)
                    } else if (request.path == "/user2") {
                        Thread.sleep(1000)
                    }
                    return MockResponse().setResponseCode(401)
                }

                if (request.path == "/user" || request.path == "/user1" || request.path == "/user2") {
                    return MockResponse().setResponseCode(200)
                        .setBody("""{ "id": "Android" }""")
                }

                return MockResponse().setResponseCode(404)
            }
        }
        server.dispatcher = dispatcher

        val tokenProvider = spyk(MockTokenProvider())

        val client = OkHttpClient.Builder()
            .authenticator(MyAuthenticator(tokenProvider))
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .addHeader("Authorization", "Bearer invalid_token")

                chain.proceed(requestBuilder.build())
            }
            .build()

        val contentType = "application/json".toMediaType()
        val apiAdapter = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(Json.asConverterFactory(contentType))
            .build()
            .create(ApiAdapter::class.java)

        val job1 = launch(UnconfinedTestDispatcher()) {
            val user = apiAdapter.getUser1()
            assertEquals(User("Android"), user)
        }

        val job2 = launch(UnconfinedTestDispatcher()) {
            val user = apiAdapter.getUser2()
            assertEquals(User("Android"), user)
        }

        job1.join()
        job2.join()

        server.shutdown()

        verify(exactly = 2) { tokenProvider.token() }
        verify { tokenProvider.refreshToken() }
        confirmVerified(tokenProvider)
    }
}

open class MockTokenProvider : TokenProvider {

    private var isRefreshed: Boolean = false

    private val _token: String
        get() = if (isRefreshed) "valid_token" else "invalid_token"

    override fun token(): String? {
        return _token
    }

    override fun refreshToken(): String? {
        isRefreshed = true
        return _token
    }
}

package net.yanzm.myapplication.data

import kotlinx.serialization.Serializable
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.http.GET

interface ApiAdapter {

    @GET("user")
    suspend fun getUser(): User
}

@Serializable
data class User(val id: String)

interface TokenProvider {

    fun token(): String?
    fun refreshToken(): String?
}

class MyAuthenticator(private val tokenProvider: TokenProvider) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val token = tokenProvider.token() ?: return null

        println("MyAuthenticator : ${System.currentTimeMillis()} : $route, $response, $token")

        synchronized(this) {
            val newToken = tokenProvider.token()

            println("MyAuthenticator2 : ${System.currentTimeMillis()} : $route, $response, $newToken")

            if (response.request.header("Authorization") != null) {

                if (newToken != token) {
                    println("MyAuthenticator3 : ${System.currentTimeMillis()} : $route, $response, $newToken")
                    return response.request
                        .newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer $newToken")
                        .build()
                }

                val updatedToken = tokenProvider.refreshToken() ?: return null

                println("MyAuthenticator4 : ${System.currentTimeMillis()} : $route, $response, $updatedToken")

                return response.request
                    .newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $updatedToken")
                    .build()
            }
        }

        return null
    }
}

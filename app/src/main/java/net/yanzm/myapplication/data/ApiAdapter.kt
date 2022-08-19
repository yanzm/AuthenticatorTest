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

    @GET("user1")
    suspend fun getUser1(): User

    @GET("user2")
    suspend fun getUser2(): User
}

@Serializable
data class User(val id: String)

interface TokenProvider {

    fun token(): String?
    fun refreshToken(): String?
}

class MyAuthenticator(private val tokenProvider: TokenProvider) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {

        println("MyAuthenticator1 : ${System.currentTimeMillis()} : $route, $response")

        synchronized(this) {
            val newToken = tokenProvider.token()
            val newAuthorization = "Bearer $newToken"

            println("MyAuthenticator2 : ${System.currentTimeMillis()} : $route, $response, $newToken")

            val oldAuthorization = response.request.header("Authorization")
            if (oldAuthorization != null) {

                if (newAuthorization != oldAuthorization) {
                    println("MyAuthenticator3 : ${System.currentTimeMillis()} : $route, $response, $newToken")
                    return response.request
                        .newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", newAuthorization)
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

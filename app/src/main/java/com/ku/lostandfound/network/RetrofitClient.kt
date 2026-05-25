package com.ku.lostandfound.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://finders-alb-2066093184.ap-northeast-2.elb.amazonaws.com/"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("KUFindersOkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val token = TokenManager.accessToken
        val path = originalRequest.url.encodedPath
        val method = originalRequest.method
        val isAuthRequest = path.endsWith("/api/v1/auth/login") ||
            path.endsWith("/api/v1/auth/join") ||
            path.endsWith("/api/v1/auth/reissue") ||
            path.endsWith("/api/v1/auth/email/send")
        val isPublicLostItemRequest = method == "GET" && (
            path == "/api/v1/lost-items" ||
                Regex("^/api/v1/lost-items/\\d+(/matches)?$").matches(path)
            )
        val isPublicFoundItemRequest = method == "GET" && (
            path == "/api/v1/found-items" ||
                Regex("^/api/v1/found-items/\\d+(/matches)?$").matches(path)
            )

        val request = if (
            token != null &&
            originalRequest.header("Authorization") == null &&
            !isAuthRequest &&
            !isPublicLostItemRequest &&
            !isPublicFoundItemRequest
        ) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val signupApi: SignupApiService = retrofit.create(SignupApiService::class.java)
    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)
    val userApi: UserApiService = retrofit.create(UserApiService::class.java)
    val lostItemApi: LostItemApiService = retrofit.create(LostItemApiService::class.java)
    val foundItemApi: FoundItemApiService = retrofit.create(FoundItemApiService::class.java)
}

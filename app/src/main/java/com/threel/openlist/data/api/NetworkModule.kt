package com.threel.openlist.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.threel.openlist.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** 全 App 共用的 kotlinx.serialization Json 实例（与 Retrofit 一致配置）。
 *  用于 OkHttp 直发接口（fs/get、fs/form、share/create）安全地解析 JSON，
 *  替代脆弱的 Regex 字符串匹配。 */
val OpenListJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            // 仅 debug 包打印网络日志，release 包关掉避免泄露请求 URL/头
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        tokenStore: TokenStore,
    ): Retrofit {
        val server = tokenStore.serverUrlSync().trimEnd('/')
        return Retrofit.Builder()
            .baseUrl(if (server.endsWith("/")) server else "$server/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenListApi(retrofit: Retrofit): OpenListApi =
        retrofit.create(OpenListApi::class.java)

    @Provides
    @Singleton
    fun provideManagementApi(retrofit: Retrofit): ManagementApi =
        retrofit.create(ManagementApi::class.java)
}

package com.threel.openlist.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.threel.openlist.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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

/** Retrofit 占位主机：真正的服务器地址在 [DynamicServerInterceptor] 里按 DataStore 动态替换。 */
private const val PLACEHOLDER_HOST = "dynamic.openlist.local"

/**
 * 把 Retrofit 发出的（占位主机）请求改写成当前配置的服务器地址。
 *
 * 仅改写主机为 [PLACEHOLDER_HOST] 的请求；仓库内 download / upload / 测试连接 等
 * 使用独立 OkHttp 客户端直连真实地址，不经过本拦截器，因此互不干扰。
 * 这样在「服务器设置」里切换地址后无需重启 App 即可生效。
 */
private class DynamicServerInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Chain): Response {
        val original = chain.request()
        val url = original.url
        if (url.host != PLACEHOLDER_HOST) return chain.proceed(original)

        val server = tokenStore.serverUrlSync().trimEnd('/')
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            return chain.proceed(original)
        }
        val newUrl = buildString {
            append(server)
            append(url.encodedPath)
            if (!url.encodedQuery.isNullOrEmpty()) append("?").append(url.encodedQuery)
        }
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
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
        tokenStore: TokenStore,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DynamicServerInterceptor(tokenStore))
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
    ): Retrofit {
        // baseUrl 用占位主机；真正的服务器地址由 DynamicServerInterceptor 在每次请求时
        // 按 TokenStore 中当前保存的 server_url 动态改写，因此切换服务器无需重启 App。
        return Retrofit.Builder()
            .baseUrl("https://$PLACEHOLDER_HOST/")
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

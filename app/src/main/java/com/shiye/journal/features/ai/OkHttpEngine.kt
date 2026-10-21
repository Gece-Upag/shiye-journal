package com.shiye.journal.features.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * OkHttp 版的 [AiHttpEngine]（规格 §2.1 指定用 OkHttp）。
 *
 * 这是**整个 App 里唯一发起网络请求的地方**。集中在一处有几个好处：
 * 想确认"AI 关掉后到底有没有联网"，只要看这里被调用没有；
 * 想加超时或换实现，也只动这一个文件。
 */
class OkHttpEngine : AiHttpEngine {

    private val client = OkHttpClient.Builder()
        // 连接/读取超时每次由调用方给（配置里的超时），这里只设一个兜底值
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutSeconds: Int
    ): String {
        // 每次请求单独设超时：用户改了配置里的超时，下一次请求就得生效，
        // 不能用构造时那个一成不变的 client
        val callClient = client.newBuilder()
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout((timeoutSeconds + 5).toLong(), TimeUnit.SECONDS)
            .build()

        // 分两步 try，因为**两种 IllegalArgumentException 说的完全是两件事**：
        // .url() 抛的 = 地址不对；addHeader() 抛的 = Key 里有脏字符（最常见是复制时带的换行）。
        // 以前合并处理，导致后者被报成"地址格式不对"，用户对着正确的地址查半天。
        val request = try {
            val builder = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
            try {
                headers.forEach { (name, value) -> builder.addHeader(name, value) }
            } catch (e: IllegalArgumentException) {
                throw AiHttpException(
                    reason = AiHttpException.AiHttpCause.BAD_CREDENTIAL,
                    message = e.message?.take(80).orEmpty()
                )
            }
            builder.build()
        } catch (e: AiHttpException) {
            throw e
        } catch (e: MalformedURLException) {
            throw AiHttpException(reason = AiHttpException.AiHttpCause.BAD_URL, message = url)
        } catch (e: IllegalArgumentException) {
            // OkHttp 对不合法的 URL 抛的是这个（区别于上面的 header 校验）
            throw AiHttpException(reason = AiHttpException.AiHttpCause.BAD_URL, message = url)
        }

        val response = try {
            callClient.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw AiHttpException(reason = AiHttpException.AiHttpCause.TIMEOUT, message = "read timeout")
        } catch (e: InterruptedIOException) {
            throw AiHttpException(reason = AiHttpException.AiHttpCause.TIMEOUT, message = "interrupted")
        } catch (e: UnknownHostException) {
            throw AiHttpException(
                reason = AiHttpException.AiHttpCause.IO,
                message = "找不到主机"
            )
        } catch (e: java.net.ConnectException) {
            throw AiHttpException(
                reason = AiHttpException.AiHttpCause.IO,
                message = "连接被拒绝"
            )
        } catch (e: java.io.IOException) {
            throw AiHttpException(
                reason = AiHttpException.AiHttpCause.IO,
                message = e.message?.take(80).orEmpty()
            )
        }

        response.use { res ->
            // 只在这里读一次 body；之后统一关闭，避免连接泄漏
            val payload = try {
                res.body?.string().orEmpty()
            } catch (e: java.io.IOException) {
                throw AiHttpException(
                    statusCode = res.code,
                    reason = AiHttpException.AiHttpCause.UNREADABLE_BODY
                )
            }
            if (!res.isSuccessful) {
                throw AiHttpException(statusCode = res.code, reason = AiHttpException.AiHttpCause.HTTP_STATUS)
            }
            return payload
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

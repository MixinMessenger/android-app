package one.mixin.android.util.cronet

import android.os.ConditionVariable
import android.util.Log.getStackTraceString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.chromium.net.CronetException
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.Locale

class CronetCallback(
    var originRequest: Request,
    val call: Call,
    private val eventListener: EventListener? = null,
    private val responseCallback: Callback? = null
) : UrlRequest.Callback() {
    private lateinit var response: Response

    private val responseCondition = ConditionVariable()

    private val bytesReceived = ByteArrayOutputStream()
    private val receiveChannel = Channels.newChannel(bytesReceived)

    private var followCount = 0

    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
        response = responseFromResponse(info)

        eventListener?.responseHeadersEnd(call, response)
        eventListener?.responseBodyStart(call)

        request.read(ByteBuffer.allocateDirect(32 * 1024))
    }

    override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
        byteBuffer.flip()

        try {
            receiveChannel.write(byteBuffer)
        } catch (e: IOException) {
            Timber.i(" $TAG IOException during ByteBuffer read. Details: $e")
            throw e
        }

        byteBuffer.clear()

        request.read(byteBuffer)
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
        eventListener?.responseBodyEnd(call, info.receivedByteCount)

        val contentTypeString = response.header("content-type")
        val contentType = contentTypeString?.toMediaTypeOrNull() ?: "text/plain; charset=\"utf-8\"".toMediaType()

        val responseBody = bytesReceived.toByteArray().toResponseBody(contentType)
        originRequest = originRequest.newBuilder().url(info.url).build()
        response = response.newBuilder().body(responseBody).build()

        responseCondition.open()

        eventListener?.callEnd(call)
        responseCallback?.onResponse(call, response)
    }

    override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
        if (followCount > MAX_FOLLOW_COUNT) {
            request.cancel()
            return
        }
        followCount++
        // TODO match OkHttp followRedirects and followSslRedirects
        request.followRedirect()
    }

    override fun onFailed(request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
        responseCondition.open()

        eventListener?.callFailed(call, error)
        responseCallback?.onFailure(call, error)
    }

    override fun onCanceled(request: UrlRequest?, info: UrlResponseInfo?) {
        responseCondition.open()

        eventListener?.callEnd(call)
    }

    internal fun waitDone(): Response {
        responseCondition.block()
        return response
    }

    private fun protocolFromNegotiatedProtocol(responseInfo: UrlResponseInfo): Protocol {
        val negotiatedProtocol = responseInfo.negotiatedProtocol.toLowerCase(Locale.getDefault())
        return when {
            negotiatedProtocol.contains("quic") -> {
                Protocol.QUIC
            }
            negotiatedProtocol.contains("h2") -> {
                Protocol.HTTP_2
            }
            negotiatedProtocol.contains("1.1") -> {
                Protocol.HTTP_1_1
            }
            else -> {
                Protocol.HTTP_1_0
            }
        }
    }

    private fun headersFromResponse(responseInfo: UrlResponseInfo): Headers {
        val headers = responseInfo.allHeadersAsList
        val headerBuilder = Headers.Builder()
        for ((key, value) in headers) {
            try {
                if (key.equals("content-encoding", ignoreCase = true)) {
                    // Strip all content encoding headers as decoding is done handled by cronet
                    continue
                }
                headerBuilder.add(key, value)
            } catch (e: Exception) {
                Timber.w("$TAG Invalid HTTP header/value: $key$value")
                // Ignore that header
            }
        }
        return headerBuilder.build()
    }

    private fun responseFromResponse(responseInfo: UrlResponseInfo): Response {
        val protocol = protocolFromNegotiatedProtocol(responseInfo)
        val headers: Headers = headersFromResponse(responseInfo)
        return Response.Builder()
            .receivedResponseAtMillis(System.currentTimeMillis())
            .request(call.request())
            .protocol(protocol)
            .code(responseInfo.httpStatusCode)
            .message(responseInfo.httpStatusText)
            .headers(headers)
            .build()
    }

    companion object {
        const val TAG = "CronetCallback"

        const val MAX_FOLLOW_COUNT = 20

        fun onRequestFinishedHandle(requestInfo: RequestFinishedInfo) {
            Timber.d("$TAG ############# url: ${requestInfo.url} #############")
            Timber.d("$TAG onRequestFinished: ${requestInfo.finishedReason}")
            val metrics = requestInfo.metrics
            if (metrics != null) {
                Timber.d("$TAG RequestStart: ${if (metrics.requestStart == null) -1 else metrics.requestStart!!.time}")
                Timber.d("$TAG DnsStart: ${if (metrics.dnsStart == null) -1 else metrics.dnsStart!!.time}")
                Timber.d("$TAG DnsEnd: ${if (metrics.dnsEnd == null) -1 else metrics.dnsEnd!!.time}")
                Timber.d("$TAG ConnectStart: ${if (metrics.connectStart == null) -1 else metrics.connectStart!!.time}")
                Timber.d("$TAG ConnectEnd: ${if (metrics.connectEnd == null) -1 else metrics.connectEnd!!.time}")
                Timber.d("$TAG SslStart: ${if (metrics.sslStart == null) -1 else metrics.sslStart!!.time}")
                Timber.d("$TAG SslEnd: ${if (metrics.sslEnd == null) -1 else metrics.sslEnd!!.time}")
                Timber.d("$TAG SendingStart: ${if (metrics.sendingStart == null) -1 else metrics.sendingStart!!.time}")
                Timber.d("$TAG SendingEnd: ${if (metrics.sendingEnd == null) -1 else metrics.sendingEnd!!.time}")
                Timber.d("$TAG PushStart: ${if (metrics.pushStart == null) -1 else metrics.pushStart!!.time}")
                Timber.d("$TAG PushEnd: ${if (metrics.pushEnd == null) -1 else metrics.pushEnd!!.time}")
                Timber.d("$TAG ResponseStart: ${if (metrics.responseStart == null) -1 else metrics.responseStart!!.time}")
                Timber.d("$TAG RequestEnd: ${if (metrics.requestEnd == null) -1 else metrics.requestEnd!!.time}")
                Timber.d("$TAG TotalTimeMs: ${metrics.totalTimeMs}")
                Timber.d("$TAG RecvByteCount: ${metrics.receivedByteCount}")
                Timber.d("$TAG SentByteCount: ${metrics.sentByteCount}")
                Timber.d("$TAG SocketReused: ${metrics.socketReused}")
                Timber.d("$TAG TtfbMs: ${metrics.ttfbMs}")
            }
            val exception: java.lang.Exception? = requestInfo.exception
            if (exception != null) {
                Timber.e(getStackTraceString(exception))
            }
            val urlResponseInfo = requestInfo.responseInfo
            if (urlResponseInfo != null) {
                Timber.d("$TAG Cache: ${urlResponseInfo.wasCached()}")
                Timber.d("$TAG Protocol: ${urlResponseInfo.negotiatedProtocol}")
                Timber.d("$TAG HttpCode: ${urlResponseInfo.httpStatusCode}")
                Timber.d("$TAG ProxyServer: ${urlResponseInfo.proxyServer}")
                val headers = urlResponseInfo.allHeadersAsList
                for ((key, value) in headers) {
                    Timber.d("$TAG === $key : $value ===")
                }
            }
            Timber.d("$TAG ############# END #############")
        }
    }
}

package ee.nx01.tonclient

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.module.kotlin.readValue
import ee.nx01.tonclient.abi.AbiModule
import ee.nx01.tonclient.boc.BocModule
import ee.nx01.tonclient.crypto.CryptoModule
import ee.nx01.tonclient.net.NetModule
import ee.nx01.tonclient.process.ProcessModule
import ee.nx01.tonclient.tvm.TvmModule
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.scijava.nativelib.NativeLoader
import ton.sdk.TONSDKJsonApi
import java.lang.Exception
import java.lang.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class TonClient(val config: TonClientConfig = TonClientConfig()) {
    private val logger = KotlinLogging.logger {}

    private var context: Int = 0
    private val mapper = JsonUtils.mapper

    val net = NetModule(this)
    val abi = AbiModule(this)
    val tvm = TvmModule(this)
    val crypto = CryptoModule(this)
    val processing = ProcessModule(this)
    val boc = BocModule(this)

    init {
        val result = TONSDKJsonApi.createContext(mapper.writeValueAsString(config))
        logger.info { "Context created: $result" }
        val response = mapper.readValue<CreateContextResponse>(result)
        context = response.result
    }


    private fun requestAsync(method: String, params: String, onResult: (result: TonClientResponse) -> Unit) {
        val requestId = Math.abs(Random.nextInt())
        logger.info { "Request to TONSDK: requestId=$requestId context=$context" }
        TONSDKJsonApi.jsonRequestAsync(context, requestId, method, params, object : Handler {
            override fun invoke(result: String, error: String, responseType: Int) {
                try {
                    onResult(TonClientResponse(result, error, TonResponseType.fromIntRepresentation(responseType)))
                } catch (e: Exception) {
                    logger.error(e.message, e)
                }
            }
        })
    }

    suspend fun subscribe(method: String, params: Any, onResult: (result: String) -> Unit): Long {
        val requestString = mapper.writeValueAsString(params)

        val mutex = Mutex(true)

        var handle = 0L

        requestAsync(method, requestString) {
            logger.info { "Response: $it"}

            if (it.responseType == TonResponseType.Success) {
                val response = mapper.readValue<SubscriptionResponse>(it.result)

                handle = response.handle

                if (mutex.isLocked) {
                    mutex.unlock()
                }
            } else if (it.responseType == TonResponseType.Custom) {
                onResult(it.result)
            }
        }

        mutex.lock()

        if (handle == 0L) {
            throw RuntimeException()
        }

        return handle
    }

    suspend fun unsubscribe(handle: Long) {
        request("net.unsubscribe", SubscriptionResponse(handle))
    }


    private suspend fun requestToSuspend(method: String, params: String): TonClientResponse = suspendCoroutine { cont ->
        requestAsync(method, params) {
            if (it.responseType == TonResponseType.Success || it.responseType == TonResponseType.Error) {
                cont.resume(it)
            }
        }
    }

    suspend fun request(method: String, params: Any): String {
        val requestString = mapper.writeValueAsString(params)

        logger.info { "Request: $requestString" }

        val response = requestToSuspend(method, requestString)

        logger.info { "Response: $response" }

        if (response.result.isNotEmpty()) {
            return response.result
        }

        throw TonClientException(mapper.readValue(response.error))
    }

    fun destroy() {
        TONSDKJsonApi.destroyContext(context)
    }

    companion object {
        init {
            NativeLoader.loadLibrary("tonclientjni");
        }
    }

    data class TonClientResponse(val result: String, val error: String, val responseType: TonResponseType)

    enum class TonResponseType(val code: Int) {
        Success(0),
        Error(1),
        Nop(2),
        Custom(100);

        companion object {
            @JsonCreator
            @JvmStatic fun fromIntRepresentation(intValue: Int): TonResponseType {
                return TonResponseType.values().firstOrNull { it.code == intValue } ?: Success
            }
        }
    }


    data class CreateContextResponse(val result: Int)
    data class SubscriptionResponse(val handle: Long)


    interface Handler {
        fun invoke(result: String, error: String, responseType: Int)
    }

}


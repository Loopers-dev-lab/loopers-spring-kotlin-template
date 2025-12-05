package com.loopers.infrastructure.pg

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.ConnectException
import java.net.SocketTimeoutException

@Component
class PgClientImpl(
    @Value("\${pg.base-url:http://localhost:8081}")
    private val baseUrl: String,
    circuitBreakerRegistry: CircuitBreakerRegistry,
    retryRegistry: RetryRegistry,
) : PgClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg")
    private val retry: Retry = retryRegistry.retry("pg")

    override fun requestPayment(
        userId: Long,
        request: PgPaymentRequest,
    ): PgPaymentResponse {
        log.info(
            "PG 결제 요청. userId={}, orderId={}, amount={}",
            userId,
            request.orderId,
            request.amount,
        )

        return executeWithResilience(userId, "결제 요청") {
            val response = restClient.post()
                .uri("/api/v1/payments")
                .header("X-USER-ID", userId.toString())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw PgException.RequestNotReached("PG 서버 오류: ${response.statusCode}")
                }
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    throw PgException.BusinessError("BAD_REQUEST", "잘못된 요청: ${response.statusCode}")
                }
                .body(object : ParameterizedTypeReference<PgResponse<PgPaymentResponse>>() {})

            handleResponse(response)
        }
    }

    override fun getPaymentByKey(
        userId: Long,
        transactionKey: String,
    ): PgPaymentDetailResponse {
        log.info("PG 결제 조회(단건). userId={}, transactionKey={}", userId, transactionKey)

        return executeWithResilience(userId, "결제 조회") {
            val response = restClient.get()
                .uri("/api/v1/payments/{transactionKey}", transactionKey)
                .header("X-USER-ID", userId.toString())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw PgException.RequestNotReached("PG 서버 오류: ${response.statusCode}")
                }
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    throw PgException.BusinessError("NOT_FOUND", "결제 정보 없음: ${response.statusCode}")
                }
                .body(object : ParameterizedTypeReference<PgResponse<PgPaymentDetailResponse>>() {})

            handleResponse(response)
        }
    }

    override fun getPaymentsByOrderId(
        userId: Long,
        orderId: String,
    ): PgPaymentListResponse {
        log.info("PG 결제 조회(주문별). userId={}, orderId={}", userId, orderId)

        return executeWithResilience(userId, "결제 목록 조회") {
            val response = restClient.get()
                .uri("/api/v1/payments?orderId={orderId}", orderId)
                .header("X-USER-ID", userId.toString())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw PgException.RequestNotReached("PG 서버 오류: ${response.statusCode}")
                }
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    throw PgException.BusinessError("NOT_FOUND", "주문 정보 없음: ${response.statusCode}")
                }
                .body(object : ParameterizedTypeReference<PgResponse<PgPaymentListResponse>>() {})

            handleResponse(response)
        }
    }

    private fun <T> executeWithResilience(
        userId: Long,
        operation: String,
        block: () -> T,
    ): T {
        // 데코레이터 순서: CircuitBreaker(Retry(actual call))
        val retrySupplier = Retry.decorateSupplier(retry) {
            try {
                block()
            } catch (e: ResourceAccessException) {
                classifyAndThrow(e)
            }
        }

        val circuitBreakerSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, retrySupplier)

        return try {
            circuitBreakerSupplier.get()
        } catch (e: CallNotPermittedException) {
            log.warn("서킷 브레이커 오픈 상태. userId={}, operation={}", userId, operation)
            throw PgException.CircuitOpen("서킷 브레이커가 열려있습니다. 잠시 후 다시 시도해주세요.")
        } catch (e: PgException) {
            throw e
        } catch (e: Exception) {
            log.error("PG 호출 중 예상치 못한 오류. userId={}, operation={}", userId, operation, e)
            throw PgException.RequestNotReached("PG 호출 실패: ${e.message}", e)
        }
    }

    private fun classifyAndThrow(e: ResourceAccessException): Nothing {
        val cause = e.cause
        when (cause) {
            is SocketTimeoutException -> {
                if (cause.message?.contains("Read timed out") == true) {
                    // Read Timeout: 요청이 도달했을 수 있음, 재시도 불가
                    throw PgException.ResponseUncertain("응답 타임아웃: 결제 상태 확인 필요", e)
                }
                // Connect Timeout: 요청 미도달, 재시도 가능
                throw PgException.RequestNotReached("연결 타임아웃", e)
            }
            is ConnectException -> {
                throw PgException.RequestNotReached("연결 실패: ${cause.message}", e)
            }
            else -> {
                throw PgException.RequestNotReached("네트워크 오류: ${e.message}", e)
            }
        }
    }

    private fun <T> handleResponse(response: PgResponse<T>?): T {
        if (response == null) {
            throw PgException.RequestNotReached("PG 응답이 비어있습니다")
        }

        if (!response.meta.isSuccess()) {
            throw PgException.BusinessError(
                errorCode = response.meta.errorCode ?: "UNKNOWN",
                message = response.meta.message ?: "알 수 없는 오류",
            )
        }

        return response.data ?: throw PgException.RequestNotReached("PG 응답 데이터가 비어있습니다")
    }
}

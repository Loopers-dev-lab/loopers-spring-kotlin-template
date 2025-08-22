package com.loopers.infrastructure.pg

import feign.FeignException
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Component

@Component
class PgGateway(
    private val pgClient: PgClient,
) {
    sealed interface Result {
        data class Ok(val transactionKey: String) : Result
        data class BadRequest(val message: String?) : Result
        data class Retryable(val message: String?) : Result
    }

    @CircuitBreaker(name = "pg")
    @Retry(name = "payment", fallbackMethod = "fallback")
    fun payment(userId: Long, req: PgDto.PaymentRequest): Result {
        val resp = pgClient.payment(userId, req)

        val transactionKey = resp.data?.transactionKey
            ?: throw IllegalStateException("트랜잭션키를 받지 못했습니다.")

        return Result.Ok(transactionKey)
    }

    fun fallback(userId: Long, request: PgDto.PaymentRequest, ex: Throwable): Result {
        return when (ex) {
            is FeignException.FeignClientException -> {
                Result.BadRequest(ex.message ?: "bad request")
            }
            is FeignException.FeignServerException -> {
                Result.Retryable("PG FeignServerException: ${ex.message ?: ""}".trim())
            }
            is RetryableException -> {
                Result.Retryable("PG RetryableException: ${ex.message ?: ""}".trim())
            }
            is java.util.concurrent.TimeoutException -> {
                Result.Retryable("PG TimeoutException: ${ex.message ?: ""}".trim())
            }
            else -> {
                Result.Retryable("PG error: ${ex.message ?: ""}".trim())
            }
        }
    }
}

package com.loopers.infrastructure.pg

import com.loopers.domain.payment.type.TransactionStatus
import com.loopers.infrastructure.pg.fixture.PgFixtures
import com.loopers.interfaces.api.ApiResponse
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration

@SpringBootTest
class PgGatewayTest @Autowired constructor(
    private val gateway: PgGateway,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    @MockitoBean private val pgClient: PgClient,
) {

    @AfterEach
    fun tearDown() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
        reset(pgClient)
    }

    @Nested
    @DisplayName("재시도/에러 처리")
    inner class RetryAndErrors {

        @Test
        fun `400대 에러이면 BadRequest를 반환하고 재시도하지 않는다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            given(pgClient.payment(userId, body)).willThrow(PgFixtures.feign400())

            val result = gateway.payment(userId, body)

            assertThat(result).isInstanceOf(PgGateway.Result.BadRequest::class.java)
            verify(pgClient, times(1)).payment(userId, body)
        }

        @Test
        fun `500대 에러가 2번 후 성공하면 Ok를 반환한다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            val ok = ApiResponse.success(
                PgDto.TransactionResponse("TRANSACTION_KEY", TransactionStatus.SUCCESS, null),
            )
            given(pgClient.payment(userId, body))
                .willThrow(PgFixtures.feign500())
                .willThrow(PgFixtures.retryable())
                .willReturn(ok)

            val result = gateway.payment(userId, body)

            assertThat(result).isInstanceOf(PgGateway.Result.Ok::class.java)
            assertThat((result as PgGateway.Result.Ok).transactionKey).isEqualTo("TRANSACTION_KEY")
            verify(pgClient, times(3)).payment(userId, body)
        }

        @Test
        fun `타임아웃이 반복되면 최종적으로 Retryable을 반환한다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            given(pgClient.payment(userId, body)).willAnswer { throw PgFixtures.retryable() }

            val result = gateway.payment(userId, body)

            assertThat(result).isInstanceOf(PgGateway.Result.Retryable::class.java)
            verify(pgClient, times(3)).payment(userId, body)
        }

        @Test
        fun `외부 장애가 발생해도 예외 대신 Result를 반환한다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            given(pgClient.payment(userId, body)).willThrow(PgFixtures.feign500())

            val result = gateway.payment(userId, body)

            assertThat(result).isInstanceOf(PgGateway.Result.Retryable::class.java)
        }

        @Test
        fun `재시도 모두 실패하면 Retryable을 반환한다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            given(pgClient.payment(userId, body))
                .willThrow(PgFixtures.retryable())
                .willThrow(PgFixtures.feign500())
                .willThrow(PgFixtures.retryable())

            val result = gateway.payment(userId, body)

            assertThat(result).isInstanceOf(PgGateway.Result.Retryable::class.java)
        }
    }

    @Nested
    @DisplayName("서킷브레이커")
    inner class CircuitBreakerBehavior {

        @Test
        fun `OPEN 상태라면 즉시 Retryable fallback이 발생한다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg")
            circuitBreaker.transitionToOpenState()

            val start = System.nanoTime()
            val result = gateway.payment(userId, body)
            val tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis()

            assertThat(result).isInstanceOf(PgGateway.Result.Retryable::class.java)
            assertThat(tookMs).isLessThan(25)
            verifyNoInteractions(pgClient)
        }

        @Test
        fun `HALF_OPEN 상태에서 성공이 허용횟수만큼 이어지면 CLOSED로 전환된다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg")
            circuitBreaker.transitionToOpenState()
            circuitBreaker.transitionToHalfOpenState()

            given(pgClient.payment(userId, body)).willReturn(
                ApiResponse.success(PgDto.TransactionResponse("TX", TransactionStatus.SUCCESS, null)),
            )

            repeat(circuitBreaker.circuitBreakerConfig.permittedNumberOfCallsInHalfOpenState) {
                gateway.payment(userId, body)
            }

            assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
        }

        @Test
        fun `HALF_OPEN 상태에서 실패가 발생하면 다시 OPEN으로 전환된다`() {
            val (userId, body) = PgFixtures.paymentRequestDto()
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg")
            circuitBreaker.transitionToOpenState()
            circuitBreaker.transitionToHalfOpenState()

            given(pgClient.payment(userId, body)).willThrow(PgFixtures.feign500())

            repeat(circuitBreaker.circuitBreakerConfig.permittedNumberOfCallsInHalfOpenState) {
                gateway.payment(userId, body)
            }

            assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)
        }
    }
}

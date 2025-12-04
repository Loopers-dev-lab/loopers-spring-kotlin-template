package com.loopers.infrastructure.payment

import com.loopers.domain.payment.dto.PgCommand
import com.loopers.domain.payment.dto.PgInfo
import com.loopers.domain.payment.PgGateway
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PgGatewayImpl(
    private val pgClient: PgClient,
    @Value("\${pg.callback-url}")
    private val callbackUrl: String,
) : PgGateway {

    private val log = LoggerFactory.getLogger(PgGatewayImpl::class.java)

    @CircuitBreaker(name = "pgClient", fallbackMethod = "requestPaymentFallback")
    override fun requestPayment(
        command: PgCommand.Request,
    ): PgInfo.Transaction {
        log.info("PG 결제 요청 시작 - command: {}", command)
        return try {
            val request = PgDto.PgRequest(
                orderId = command.orderId,
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = command.amount,
                callbackUrl = callbackUrl,
            )
            val response = pgClient.requestPayment(command.userId, request)
            val transaction = response.data?.toDomain()
                ?: throw CoreException(ErrorType.PG_SYSTEM_ERROR, "PG 응답 데이터가 없습니다.")

            log.info(
                "PG 결제 요청 성공 - userId: {}, transactionKey: {}, status: {}",
                command.userId,
                transaction.transactionKey,
                transaction.status,
            )
            transaction
        } catch (e: CallNotPermittedException) {
            log.error("PG requestPayment 서킷브레이커 오픈 - userId: {}, orderId: {}", command.userId, command.orderId, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "PG 서비스가 현재 사용 불가능합니다. 잠시 후 다시 시도해주세요.")
        } catch (e: CoreException) {
            throw e
        } catch (e: Exception) {
            log.error("PG 결제 요청 실패 - userId: {}, orderId: {}", command.userId, command.orderId, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 처리 중 오류가 발생했습니다.")
        }
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "getPaymentFallback")
    override fun getPayment(
        userId: String,
        transactionKey: String,
    ): PgInfo.Transaction? {
        log.info("PG 거래 조회 시작 - userId: {}, transactionKey: {}", userId, transactionKey)
        return try {
            val response = pgClient.getPayment(userId, transactionKey)
            val transaction = response.data?.toDomain()

            if (transaction != null) {
                log.info("PG 거래 조회 성공 - userId: {}, transactionKey: {}", userId, transactionKey)
            } else {
                log.warn("PG 거래 조회 결과 없음 - userId: {}, transactionKey: {}", userId, transactionKey)
            }
            transaction
        } catch (e: CallNotPermittedException) {
            log.error("PG getPayment 서킷브레이커 오픈 - userId: {}, transactionKey: {}", userId, transactionKey, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "PG 서비스가 현재 사용 불가능합니다. 잠시 후 다시 시도해주세요.")
        } catch (e: CoreException) {
            throw e
        } catch (e: Exception) {
            log.error("PG 거래 조회 실패 - userId: {}, transactionKey: {}", userId, transactionKey, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 조회해주세요.")
        }
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "getPaymentByOrderIdFallback")
    override fun getPaymentByOrderId(
        userId: String,
        orderId: String,
    ): PgInfo.Order? {
        log.info("PG 주문별 결제 조회 시작 - userId: {}, orderId: {}", userId, orderId)
        return try {
            val response = pgClient.getPaymentByOrderId(userId, orderId)
            val order = response.data?.toDomain()

            if (order != null) {
                log.info("PG 주문별 결제 조회 성공 - userId: {}, orderId: {}", userId, orderId)
            } else {
                log.warn("PG 주문별 결제 조회 결과 없음 - userId: {}, orderId: {}", userId, orderId)
            }
            order
        } catch (e: CallNotPermittedException) {
            log.error("PG getPaymentByOrderId 서킷브레이커 오픈 - userId: {}, orderId: {}", userId, orderId, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "PG 서비스가 현재 사용 불가능합니다. 잠시 후 다시 시도해주세요.")
        } catch (e: CoreException) {
            throw e
        } catch (e: Exception) {
            log.error("PG 주문별 결제 조회 실패 - userId: {}, orderId: {}", userId, orderId, e)
            throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 조회해주세요.")
        }
    }

    // Fallback 메서드들
    private fun requestPaymentFallback(
        command: PgCommand.Request,
        e: Exception,
    ): PgInfo.Transaction {
        log.error("PG 결제 요청 Circuit Breaker 활성화 - userId: {}, orderId: {}", command.userId, command.orderId, e)
        throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }

    private fun getPaymentFallback(
        userId: String,
        transactionKey: String,
        e: Exception,
    ): PgInfo.Transaction? {
        log.warn("PG 거래 조회 Circuit Breaker 활성화 - userId: {}, transactionKey: {}", userId, transactionKey, e)
        throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 조회해주세요.")
    }

    private fun getPaymentByOrderIdFallback(
        userId: String,
        orderId: String,
        e: Exception,
    ): PgInfo.Order? {
        log.warn("PG 주문별 결제 조회 Circuit Breaker 활성화 - userId: {}, orderId: {}", userId, orderId, e)
        throw CoreException(ErrorType.PG_SYSTEM_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 조회해주세요.")
    }
}

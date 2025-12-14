package com.loopers.infrastructure.integration

import com.loopers.domain.integration.DataPlatformPublisher
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.payment.PaymentEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DataPlatformPublisherImpl : DataPlatformPublisher {

    private val logger = LoggerFactory.getLogger(DataPlatformPublisherImpl::class.java)

    override fun send(event: OrderEvent.OrderCreated) {
        logger.info(
            "데이터 플랫폼 전송 (주문 생성): orderId=${event.orderId}, " +
                    "userId=${event.userId}, " +
                    "couponId=${event.couponId}",
        )
        // TODO: 실제 데이터 플랫폼 전송 로직 구현 (Kafka, SQS 등)
    }

    override fun send(event: OrderEvent.OrderFailed) {
        logger.info(
            "데이터 플랫폼 전송 (주문 실패): orderId=${event.orderId}, " +
                    "userId=${event.userId}, " +
                    "reason=${event.reason}",
        )
        // TODO: 실제 데이터 플랫폼 전송 로직 구현 (Kafka, SQS 등)
    }

    override fun send(event: OrderEvent.OrderCompleted) {
        logger.info(
            "데이터 플랫폼 전송 (주문 완료): orderId=${event.orderId}, " +
                    "userId=${event.userId}, " +
                    "totalAmount=${event.totalAmount}, " +
                    "items=${event.items.size}개",
        )
        // TODO: 실제 데이터 플랫폼 전송 로직 구현 (Kafka, SQS 등)
    }

    override fun send(event: PaymentEvent.PaymentSucceeded) {
        logger.info(
            "데이터 플랫폼 전송 (결제 성공): paymentId=${event.paymentId}, " +
                    "orderId=${event.orderId}, " +
                    "userId=${event.userId}, " +
                    "totalAmount=${event.totalAmount}",
        )
        // TODO: 실제 데이터 플랫폼 전송 로직 구현 (Kafka, SQS 등)
    }

    override fun send(event: PaymentEvent.PaymentFailed) {
        logger.info(
            "데이터 플랫폼 전송 (결제 실패): paymentId=${event.paymentId}, " +
                    "orderId=${event.orderId}, " +
                    "userId=${event.userId}, " +
                    "reason=${event.reason}",
        )
        // TODO: 실제 데이터 플랫폼 전송 로직 구현 (Kafka, SQS 등)
    }
}

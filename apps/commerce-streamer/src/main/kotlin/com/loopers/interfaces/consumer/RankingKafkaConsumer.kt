package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.facade.RankingEventFacade
import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 랭킹 집계를 위한 Kafka Consumer
 *
 * Consumer 설정:
 * - Group ID: ranking-consumer-group (Metrics Consumer와 독립)
 * - Container Factory: batchKafkaListenerContainerFactory (배치 처리)
 * - Topics: catalog-events, order-events
 * - ACK Mode: Manual (처리 성공 시에만 Offset Commit)
 *
 * 왜 배치 리스너를 사용하는가?
 * - 단건 처리: 이벤트 1000개 = Redis 연산 1000번
 * - 배치 처리: 이벤트 1000개 → 상품 100개 = Redis 연산 100번
 * - 네트워크 왕복 횟수 감소 → 처리 속도 10배 향상 가능
 */
@Component
class RankingKafkaConsumer(
    private val rankingEventFacade: RankingEventFacade,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = "ranking-consumer-group", // Metrics Consumer와 다른 그룹
        containerFactory = "batchKafkaListenerContainerFactory",
    )
    fun consumeBatch(
        messages: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment,
    ) {
        logger.info("랭킹 배치 메시지 수신: ${messages.size}개")

        try {
            // 1. JSON 파싱
            val events = messages.mapNotNull { record ->
                try {
                    parseEvent(record.value())
                } catch (e: Exception) {
                    logger.error("개별 메시지 파싱 실패: key=${record.key()}, partition=${record.partition()}, offset=${record.offset()}", e)
                    null
                }
            }

            if (events.isEmpty()) {
                logger.warn("파싱 가능한 이벤트가 없음: 전체 ${messages.size}개 중 0개")
                acknowledgment.acknowledge() // 파싱 실패한 메시지는 건너뛰고 ACK
                return
            }

            // 2. Facade를 통한 비즈니스 로직 실행
            rankingEventFacade.handleBatchEvents(events)

            // 3. Offset Commit (성공 시에만)
            acknowledgment.acknowledge()
            logger.info("랭킹 배치 처리 완료: ${events.size}개")

        } catch (e: Exception) {
            logger.error("랭킹 배치 처리 실패: ${messages.size}개, error=${e.message}", e)
            // ACK 하지 않음 → 다음번에 재처리됨 (멱등성 보장으로 안전)
        }
    }

    /**
     * JSON 메시지를 DomainEvent 객체로 파싱
     *
     * 왜 eventType 필드를 먼저 읽는가?
     * - Jackson은 다형성 역직렬화를 위해 타입 정보 필요
     * - eventType을 읽고 적절한 클래스로 변환
     *
     * @param message JSON 문자열
     * @return 파싱된 DomainEvent, 또는 지원하지 않는 타입인 경우 null
     * @throws IllegalArgumentException eventType이 없는 경우
     */
    private fun parseEvent(message: String): DomainEvent? {
        try {
            val node = objectMapper.readTree(message)
            val eventTypeNode = node.get("eventType")
            
            if (eventTypeNode == null || eventTypeNode.isNull) {
                logger.error("eventType 필드가 없음. 전체 노드: $node, message: $message")
                throw IllegalArgumentException("Missing eventType in message: $message")
            }
            
            val eventType = eventTypeNode.asText()

            return when (eventType) {
                "PRODUCT_VIEWED" -> objectMapper.readValue(message, ProductViewedEvent::class.java)
                "PRODUCT_LIKED" -> objectMapper.readValue(message, ProductLikedEvent::class.java)
                "PRODUCT_UNLIKED" -> objectMapper.readValue(message, ProductUnlikedEvent::class.java)
                "ORDER_CREATED" -> objectMapper.readValue(message, OrderCreatedEvent::class.java)
                // 랭킹과 무관한 이벤트는 무시 (예: STOCK_DECREASED, PAYMENT_COMPLETED)
                else -> {
                    logger.debug("랭킹 처리 대상 아님: eventType=$eventType")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("이벤트 파싱 실패: message=$message", e)
            throw e
        }
    }
}

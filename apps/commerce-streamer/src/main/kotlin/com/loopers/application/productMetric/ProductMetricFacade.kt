package com.loopers.application.productMetric

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandleService
import com.loopers.domain.productMetric.ProductMetricService
import com.loopers.event.CatalogEventPayload
import com.loopers.event.CatalogType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class ProductMetricFacade(
    private val productMetricService: ProductMetricService,
    private val eventHandleService: EventHandleService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val logger = LoggerFactory.getLogger(ProductMetricService::class.java)

    @Transactional
    fun updateProductMetrics(messages: List<ConsumerRecord<String, String>>): Set<Long> {
        val commands = buildCommands(messages)
        val likeCountCommand = commands.first
        val viewCountCommand = commands.second

        val updatedProductIds = mutableSetOf<Long>()

        if (likeCountCommand.likeCountGroupBy.isNotEmpty()) {
            productMetricService.updateLikeCount(likeCountCommand.likeCountGroupBy)
            updatedProductIds.addAll(likeCountCommand.likeCountGroupBy.keys)
        }
        if (viewCountCommand.viewCountGroupBy.isNotEmpty()) {
            productMetricService.updateViewCount(viewCountCommand.viewCountGroupBy)
            updatedProductIds.addAll(viewCountCommand.viewCountGroupBy.keys)
        }

        return updatedProductIds
    }

    private fun buildCommands(
        messages: List<ConsumerRecord<String, String>>,
    ): Pair<LikeCountUpdateCommand, ViewCountUpdateCommand> {
        val eventIds = mutableSetOf<String>()
        val likeCountGroupBy = mutableMapOf<Long, Long>()
        val viewCountGroupBy = mutableMapOf<Long, Long>()

        messages.forEach { record ->
            val payload = objectMapper.readValue(record.value(), CatalogEventPayload::class.java)
            val eventId = payload.eventId

            if (eventIds.contains(eventId)) {
                return@forEach
            }

            if (eventHandleService.duplicatedBy(eventId)) {
                return@forEach
            }

            when (payload.type) {
                CatalogType.LIKED -> {
                    likeCountGroupBy[payload.productId] =
                        likeCountGroupBy.getOrPut(payload.productId) { 0L } + 1L
                }

                CatalogType.UNLIKED -> {
                    likeCountGroupBy[payload.productId] =
                        likeCountGroupBy.getOrPut(payload.productId) { 0L } - 1L
                }

                CatalogType.DETAIL_VIEW -> {
                    viewCountGroupBy[payload.productId] =
                        viewCountGroupBy.getOrPut(payload.productId) { 0L } + 1L
                }
            }

            eventIds.add(eventId)
            eventHandleService.ensureCatalogEvent(eventId)
        }

        return Pair(
            LikeCountUpdateCommand(likeCountGroupBy = likeCountGroupBy),
            ViewCountUpdateCommand(viewCountGroupBy = viewCountGroupBy),
        )
    }

    /** 변경된 상품들에 대해 즉시 랭킹 업데이트 */
    fun updateRankingForProducts(productIds: Set<Long>) {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val formattedDate = LocalDate.now().format(dateFormatter)
        val key = "LOOPERS::ranking-v1:$formattedDate"

        logger.debug("Updating ranking for ${productIds.size} products on date: $formattedDate")

        val metrics = productMetricService.getMetricsByProductIds(productIds)

        if (metrics.isEmpty()) {
            logger.warn("No metrics found for productIds: $productIds")
            return
        }

        metrics.forEach { metric ->
            // 전 시간의 랭킹 점수 가져오기 (없으면 0)
            val previousScore = zSetOps.score(key, metric.refProductId.toString()) ?: 0.0

            val score =
                calculateRankingScore(
                    viewCount = metric.viewCount,
                    likeCount = metric.likeCount,
                    salesCount = metric.salesCount,
                    previousHourScore = previousScore,
                )

            // 기존 점수를 새로운 점수로 설정 (incrementScore가 아닌 add 사용)
            zSetOps.add(key, metric.refProductId.toString(), score)
        }

        redisTemplate.expire(key, Duration.ofDays(2))

        logger.debug("Ranking update completed for ${metrics.size} products")
    }

    /**
     * 랭킹 점수 계산: 조회: Weight = 0.1, Score = viewCount 좋아요: Weight = 0.2, Score = likeCount 주문: Weight
     * = 0.6, Score = salesCount 전 시간: Weight = 0.1, Score = previousHourScore
     */
    private fun calculateRankingScore(
        viewCount: Long,
        likeCount: Long,
        salesCount: Long,
        previousHourScore: Double = 0.0,
    ): Double {
        val viewScore = 0.1 * viewCount
        val likeScore = 0.2 * likeCount
        val orderScore = 0.6 * salesCount
        val previousScore = 0.1 * previousHourScore

        return viewScore + likeScore + orderScore + previousScore
    }
}

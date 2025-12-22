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
    fun updateProductMetrics(messages: List<ConsumerRecord<String, String>>) {
        val commands = buildCommands(messages)
        val likeCountCommand = commands.first
        val viewCountCommand = commands.second

        if (likeCountCommand.likeCountGroupBy.isNotEmpty()) {
            productMetricService.updateLikeCount(likeCountCommand.likeCountGroupBy)
        }
        if (viewCountCommand.viewCountGroupBy.isNotEmpty()) {
            productMetricService.updateViewCount(viewCountCommand.viewCountGroupBy)
        }
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

    @Transactional(readOnly = true)
    fun updateRanking() {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val key = "ranking:all:$date"

        logger.info("Starting ranking update for date: $date")

        val metrics = productMetricService.getAllMetrics()

        if (metrics.isEmpty()) {
            logger.warn("No metrics found in database")
            return
        }

        val scoreUpdates = metrics.associate { metric ->
            val score = calculateRankingScore(
                viewCount = metric.viewCount,
                likeCount = metric.likeCount,
                salesCount = metric.salesCount,
            )

            metric.refProductId to score
        }

        scoreUpdates.forEach { (productId, score) ->
            zSetOps.incrementScore(key, productId.toString(), score)
        }

        redisTemplate.expire(key, Duration.ofDays(2))

        logger.info("Ranking update completed. Updated ${scoreUpdates.size} products")
    }

    /**
     * 랭킹 점수 계산
     * 조회: Weight = 0.1, Score = viewCount
     * 좋아요: Weight = 0.2, Score = likeCount
     * 주문: Weight = 0.6, Score = salesCount
     */
    private fun calculateRankingScore(
        viewCount: Long,
        likeCount: Long,
        salesCount: Long,
    ): Double {
        val viewScore = 0.1 * viewCount
        val likeScore = 0.2 * likeCount
        val orderScore = 0.6 * salesCount

        return viewScore + likeScore + orderScore
    }
}

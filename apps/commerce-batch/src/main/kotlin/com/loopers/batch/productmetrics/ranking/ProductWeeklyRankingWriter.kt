package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import com.loopers.domain.ranking.ProductWeeklyRankingRepository
import com.loopers.domain.ranking.dto.RankedProduct
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 주간 상품 랭킹 Writer
 *
 * RankedProduct를 받아서 productId별로 점수를 합산하고 Top 100을 저장
 */
@Component
@StepScope
class ProductWeeklyRankingWriter(
    private val repository: ProductWeeklyRankingRepository,
    @Value("#{jobParameters['weekStart']}") private val weekStart: String,
    @Value("#{jobParameters['weekEnd']}") private val weekEnd: String,
) : ItemWriter<RankedProduct> {

    private val log = LoggerFactory.getLogger(ProductWeeklyRankingWriter::class.java)

    @Transactional
    override fun write(chunk: Chunk<out RankedProduct>) {
        val items = chunk.items

        if (items.isEmpty()) {
            return
        }

        val start = LocalDate.parse(weekStart)
        val end = LocalDate.parse(weekEnd)

        // 기존 랭킹 데이터 로드
        val existingRankings = repository.findByWeekStartAndWeekEnd(start, end)
        val existingScores = existingRankings.associate { it.productId to it.score.toDouble() }

        // 기존 데이터와 새 데이터 병합 (productId별로 점수 합산)
        val mergedScores = mutableMapOf<Long, Double>()

        // 기존 데이터 추가
        existingScores.forEach { (productId, score) ->
            mergedScores[productId] = score
        }

        // 새 데이터 추가 (이미 있으면 합산)
        items.forEach { rankedProduct ->
            mergedScores[rankedProduct.productId] =
                mergedScores.getOrDefault(rankedProduct.productId, 0.0) + rankedProduct.finalScore
        }

        // Top 100 추출 (점수 내림차순)
        val top100 = mergedScores
            .map { (productId, score) -> RankedProduct(productId, score) }
            .sortedByDescending { it.finalScore }
            .take(100)

        // 기존 데이터 삭제 (Top 100으로 교체하기 위해)
        if (existingRankings.isNotEmpty()) {
            repository.deleteByWeekStartAndWeekEnd(start, end)
        }

        // 엔티티로 변환 (순위 포함)
        val entities = top100.mapIndexed { index, rankedProduct ->
            ProductWeeklyRanking.create(
                ranking = index + 1,
                productId = rankedProduct.productId,
                score = rankedProduct.finalScore,
                weekStart = start,
                weekEnd = end,
            )
        }

        repository.saveAll(entities)

        log.info(
            "Saved {} weekly rankings (Top 100) for week {} to {}",
            entities.size,
            start,
            end,
        )
    }
}

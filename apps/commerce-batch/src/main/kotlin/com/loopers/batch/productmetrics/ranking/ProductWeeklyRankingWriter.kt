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

        // Job 파라미터를 주차 범위로 파싱
        val start = LocalDate.parse(weekStart)
        val end = LocalDate.parse(weekEnd)

        // 점수 기준 Top 100만 유지
        val top100 = items
            .sortedByDescending { it.finalScore }
            .take(100)

        // 동일 주차 데이터는 삭제 후 재적재하여 멱등성 확보
        repository.deleteByWeekStartAndWeekEnd(start, end)

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

package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.ProductMonthlyRanking
import com.loopers.domain.ranking.ProductMonthlyRankingRepository
import com.loopers.domain.ranking.dto.RankedProduct
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

/**
 * 월간 상품 랭킹 Writer
 *
 * RankedProduct를 받아서 productId별로 점수를 합산하고 Top 100을 저장
 */
@Component
@StepScope
class ProductMonthlyRankingWriter(
    private val repository: ProductMonthlyRankingRepository,
    @Value("#{jobParameters['yearMonth']}") private val yearMonth: String,
) : ItemWriter<RankedProduct> {

    private val log = LoggerFactory.getLogger(ProductMonthlyRankingWriter::class.java)

    @Transactional
    override fun write(chunk: Chunk<out RankedProduct>) {
        val items = chunk.items

        if (items.isEmpty()) {
            return
        }

        // Job 파라미터를 월 단위로 파싱
        val monthPeriod = YearMonth.parse(yearMonth)

        // 점수 기준 Top 100만 유지
        val top100 = items
            .sortedByDescending { it.finalScore }
            .take(100)

        // 동일 월 데이터는 삭제 후 재적재하여 멱등성 확보
        repository.deleteByMonthPeriod(monthPeriod)

        val entities = top100.mapIndexed { index, rankedProduct ->
            ProductMonthlyRanking.create(
                ranking = index + 1,
                productId = rankedProduct.productId,
                score = rankedProduct.finalScore,
                monthPeriod = monthPeriod,
            )
        }

        repository.saveAll(entities)

        log.info(
            "Saved {} monthly rankings (Top 100) for month {}",
            entities.size,
            monthPeriod,
        )
    }
}

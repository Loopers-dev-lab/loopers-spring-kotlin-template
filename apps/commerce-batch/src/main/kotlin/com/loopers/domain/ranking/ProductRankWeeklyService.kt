package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductRankWeeklyService(
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
) {

    @Transactional
    fun deleteByDateTime(dateTime: String) {
        productRankWeeklyRepository.deleteByDateTime(dateTime)
    }

    @Transactional(readOnly = true)
    fun findByRefProductIdAndDateTime(refProductId: Long, dateTime: String): ProductRankWeekly? =
        productRankWeeklyRepository.findByRefProductIdAndDateTime(refProductId, dateTime)

    @Transactional(readOnly = true)
    fun findByDateTimeOrderByScoreDesc(dateTime: String): List<ProductRankWeekly> =
        productRankWeeklyRepository.findByDateTimeOrderByScoreDesc(dateTime)

    @Transactional
    fun saveOrUpdateScore(ranking: ProductRankWeekly, mondayDate: String) {
        val existing = productRankWeeklyRepository.findByRefProductIdAndDateTime(
            ranking.refProductId,
            mondayDate,
        )

        if (existing != null) {
            val updatedRanking = ProductRankWeekly.create(
                refProductId = existing.refProductId,
                score = existing.score + ranking.score,
                likeCount = existing.likeCount + ranking.likeCount,
                viewCount = existing.viewCount + ranking.viewCount,
                salesCount = existing.salesCount + ranking.salesCount,
            )
            productRankWeeklyRepository.delete(existing)
            productRankWeeklyRepository.save(updatedRanking)
        } else {
            productRankWeeklyRepository.save(ranking)
        }
    }

    @Transactional
    fun updateRanks(mondayDate: String): Int {
        val rankings = productRankWeeklyRepository.findByDateTimeOrderByScoreDesc(mondayDate)

        rankings.forEachIndexed { index, ranking ->
            val updatedRanking = ProductRankWeekly.create(
                refProductId = ranking.refProductId,
                score = ranking.score,
                likeCount = ranking.likeCount,
                viewCount = ranking.viewCount,
                salesCount = ranking.salesCount,
            )
            productRankWeeklyRepository.delete(ranking)
            productRankWeeklyRepository.save(updatedRanking)
        }

        return rankings.size
    }
}


package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import org.springframework.stereotype.Component

@Component
class ProductRankWeeklyRepositoryImpl(
    private val productRankWeeklyJpaRepository: ProductRankWeeklyJpaRepository,
) : ProductRankWeeklyRepository {

    override fun findByRefProductIdAndDateTime(refProductId: Long, dateTime: String): ProductRankWeekly? =
        productRankWeeklyJpaRepository.findByRefProductIdAndDateTime(refProductId, dateTime)

    override fun findByDateTimeOrderByScoreDesc(dateTime: String): List<ProductRankWeekly> =
        productRankWeeklyJpaRepository.findByDateTimeOrderByScoreDesc(dateTime)

    override fun deleteByDateTime(dateTime: String) =
        productRankWeeklyJpaRepository.deleteByDateTime(dateTime)

    override fun delete(productRankWeekly: ProductRankWeekly) =
        productRankWeeklyJpaRepository.delete(productRankWeekly)

    override fun save(productRankWeekly: ProductRankWeekly): ProductRankWeekly =
        productRankWeeklyJpaRepository.save(productRankWeekly)
}


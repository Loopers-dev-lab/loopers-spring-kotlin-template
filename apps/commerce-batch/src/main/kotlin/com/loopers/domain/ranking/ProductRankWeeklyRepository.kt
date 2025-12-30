package com.loopers.domain.ranking

interface ProductRankWeeklyRepository {

    fun findByRefProductIdAndDateTime(refProductId: Long, dateTime: String): ProductRankWeekly?

    fun findByDateTimeOrderByScoreDesc(dateTime: String): List<ProductRankWeekly>

    fun deleteByDateTime(dateTime: String)

    fun delete(productRankWeekly: ProductRankWeekly)

    fun save(productRankWeekly: ProductRankWeekly): ProductRankWeekly
}


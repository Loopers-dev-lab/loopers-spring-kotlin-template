package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class RankingQuery(
    val period: RankingPeriod,
    val bucketKey: String,
    val offset: Long,
    val limit: Long,
) {
    init {
        if (offset < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "offset은 0 이상이어야 합니다.")
        }
        if (limit <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "limit은 1 이상이어야 합니다.")
        }
        if (limit > MAX_LIMIT) {
            throw CoreException(ErrorType.BAD_REQUEST, "limit은 최대 ${MAX_LIMIT}까지 가능합니다.")
        }
    }

    companion object {
        private const val MAX_LIMIT = 100L
    }
}

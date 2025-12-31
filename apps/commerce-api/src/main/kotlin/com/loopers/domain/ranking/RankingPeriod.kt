package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    companion object {
        fun from(value: String?): RankingPeriod {
            if (value.isNullOrBlank()) {
                return DAILY
            }

            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "랭킹 기간 형식이 올바르지 않습니다: $value")
        }
    }
}

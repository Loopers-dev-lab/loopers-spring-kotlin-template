package com.loopers.domain.like.vo

import com.loopers.domain.like.policy.LikeCountValidator
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class LikeCountValue(
    @Column(name = "count", nullable = false)
    val value: Long,
) {
    init {
        LikeCountValidator.validateLikeCount(value)
    }

    fun increase(): LikeCountValue = LikeCountValue(value + 1)

    fun decrease(): LikeCountValue {
        if (value <= 0) throw CoreException(ErrorType.CONFLICT, "좋아요 수는 0 미만일 수 없습니다.")
        return LikeCountValue(value - 1)
    }
}

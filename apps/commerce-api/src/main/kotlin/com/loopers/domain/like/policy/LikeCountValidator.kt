package com.loopers.domain.like.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object LikeCountValidator {
    fun validateLikeCount(count: Long) {
        if (LikeCountPolicy.Min.VALUE > count) {
            throw CoreException(ErrorType.BAD_REQUEST, LikeCountPolicy.Min.MESSAGE)
        }
    }
}

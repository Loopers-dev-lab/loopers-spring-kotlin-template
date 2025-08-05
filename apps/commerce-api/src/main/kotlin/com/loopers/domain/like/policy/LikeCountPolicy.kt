package com.loopers.domain.like.policy

object LikeCountPolicy {
    object Min {
        const val VALUE = 0
        const val MESSAGE = "$VALUE 이하의 정수가 들어갈 수 없습니다."
    }
}

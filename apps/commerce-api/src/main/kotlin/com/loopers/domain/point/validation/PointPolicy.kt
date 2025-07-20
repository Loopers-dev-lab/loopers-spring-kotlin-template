package com.loopers.domain.point.validation

object PointPolicy {
    object Min {
        const val VALUE = 0
        const val MESSAGE = "$VALUE 이하의 정수가 들어갈 수 없습니다."
    }
}

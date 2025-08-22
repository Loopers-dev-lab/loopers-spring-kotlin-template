package com.loopers.domain.payment.type

enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
    ;

    companion object {
        fun of(type: String?): CardType =
            values().firstOrNull { it.name.equals(type, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 카드 타입: $type")
    }
}

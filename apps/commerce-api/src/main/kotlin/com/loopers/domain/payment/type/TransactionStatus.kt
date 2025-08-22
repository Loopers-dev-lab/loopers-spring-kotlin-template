package com.loopers.domain.payment.type

enum class TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    ;

    companion object {
        fun of(type: String?): TransactionStatus =
            values().firstOrNull { it.name.equals(type, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 카드 타입: $type")
    }
}

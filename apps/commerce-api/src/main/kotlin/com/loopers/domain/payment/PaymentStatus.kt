package com.loopers.domain.payment

enum class PaymentStatus(
    val description: String
) {
    PENDING("결제 대기"),
    SUCCESS("결제 성공"),
    FAILED("결제 실패"),
    TIMEOUT("결제 타임아웃"),
    CANCELED("결제 취소")
    ;


    fun isPending() = this == PENDING
    fun isSuccess() = this == SUCCESS
    fun isFailed() = this == FAILED
    fun isTimeout() = this == TIMEOUT
    fun isCanceled() = this == CANCELED
}

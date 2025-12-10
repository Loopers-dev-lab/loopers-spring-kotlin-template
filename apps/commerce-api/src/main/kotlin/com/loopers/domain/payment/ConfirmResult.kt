package com.loopers.domain.payment

sealed class ConfirmResult {
    data class Paid(val payment: Payment) : ConfirmResult()
    data class Failed(val payment: Payment) : ConfirmResult()
    data class StillInProgress(val payment: Payment) : ConfirmResult()
}

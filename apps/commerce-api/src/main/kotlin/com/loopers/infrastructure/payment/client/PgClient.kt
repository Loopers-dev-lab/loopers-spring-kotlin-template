package com.loopers.infrastructure.payment.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pg-client",
    url = $$"${pg.base-url}",
    configuration = [PgClientConfig::class],
)
interface PgClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgPaymentRequest,
    ): PgApiResponse<PgTransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): PgApiResponse<PgTransactionDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getTransactionsByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): PgApiResponse<PgOrderResponse>
}

data class PgPaymentRequest(
    val orderId: String,
    val cardType: CardTypeDto,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PgTransactionResponse(val transactionKey: String, val status: TransactionStatusDto, val reason: String?)

data class PgTransactionDetailResponse(
    val transactionKey: String,
    val orderId: String,
    val cardType: CardTypeDto,
    val cardNo: String,
    val amount: Long,
    val status: TransactionStatusDto,
    val reason: String?,
)

data class PgOrderResponse(val orderId: String, val transactions: List<PgTransactionResponse>)

data class PgApiResponse<T>(val success: Boolean, val data: T?, val error: PgErrorResponse?)

data class PgErrorResponse(val message: String, val code: String?)

enum class CardTypeDto {
    SAMSUNG,
    KB,
    HYUNDAI,
}

enum class TransactionStatusDto {
    PENDING,
    SUCCESS,
    FAILED,
}

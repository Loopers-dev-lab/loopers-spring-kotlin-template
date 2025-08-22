package com.loopers.infrastructure.pg.fixture

import com.loopers.domain.payment.type.CardType
import com.loopers.infrastructure.pg.PgDto
import feign.FeignException
import feign.Request
import feign.RequestTemplate
import feign.RetryableException
import java.nio.charset.StandardCharsets

object PgFixtures {
    fun paymentRequestDto(userId: Long = 1L) =
        PgDto.PaymentRequest(
            orderId = "LOOPERS-1",
            cardType = CardType.KB,
            cardNo = "1111-2222-3333-4444",
            amount = 1000L,
            callbackUrl = "http://localhost:8080/api/v1/payments/webhook",
        ).also { it.validate() }.let { userId to it }

    fun feignRequest(): Request =
        Request.create(
            Request.HttpMethod.POST,
            "/api/v1/payments",
            mapOf(),
            ByteArray(0),
            StandardCharsets.UTF_8,
            RequestTemplate(),
        )

    fun feign400(): FeignException =
        FeignException.BadRequest("400", feignRequest(), null, emptyMap())

    fun feign500(): FeignException =
        FeignException.InternalServerError("500", feignRequest(), null, emptyMap())

    fun retryable(): RetryableException =
        RetryableException(0, "io timeout", Request.HttpMethod.POST, 0L, feignRequest())
}

package com.loopers.interfaces.point

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.v1.point.PointV1Request
import com.loopers.interfaces.api.v1.point.PointV1Response
import com.loopers.interfaces.point.PointV1ApiTest.Companion.ENDPOINT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

class PointApiFixture(
    private val testRestTemplate: TestRestTemplate,
) {

    fun charge(
        userId: String = "user123",
        amount: Long = 2000L,
    ): ResponseEntity<ApiResponse<PointV1Response.Charge>> {
        val request = PointV1Request.Charge(userId, amount)
        val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.Charge>>() {}

        val response = testRestTemplate.exchange(
            ENDPOINT,
            HttpMethod.POST,
            HttpEntity(request),
            responseType,
        )

        return response
    }
}

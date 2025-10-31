package com.loopers.interfaces.user

import com.loopers.domain.user.Gender
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.v1.user.UserV1Request
import com.loopers.interfaces.api.v1.user.UserV1Response
import com.loopers.interfaces.user.UserV1ApiTest.Companion.ENDPOINT_POST
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

class UserApiFixture(
    private val testRestTemplate: TestRestTemplate,
) {
    fun create(
        userId: String = "user123",
        email: String = "test@example.com",
        birthDate: String = "2000-01-01",
        gender: Gender = Gender.MALE,
    ): ResponseEntity<ApiResponse<UserV1Response.SignUp>> {
        val request = UserV1Request.SignUp(userId, email, birthDate, gender)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
        val response = testRestTemplate.exchange(
            ENDPOINT_POST,
            HttpMethod.POST,
            HttpEntity(request, headers),
            responseType,
        )

        return response
    }
}

package com.loopers.interfaces.api.user.fixture

import com.loopers.domain.user.User
import com.loopers.domain.user.User.Gender
import com.loopers.domain.user.User.Gender.MALE
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto.UserSignUpRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

object UserV1ApiE2EFixture {
    fun getHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
    }

    fun getHeaders(userName: String): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userName)
        }
    }

    fun saveUser(
        userRepository: UserJpaRepository,
        name: String = "userName",
        gender: Gender = MALE,
        birthDate: String = "1990-01-01",
        email: String = "xx@yy.zz",
    ): User {
        return userRepository.save(
            User.create(
                name,
                gender,
                birthDate,
                email,
            ),
        )
    }

    fun givenUserSignUpRequest(
        name: String = "userName",
        gender: Gender = MALE,
        birthDate: String = "1990-01-01",
        email: String = "xx@yy.zz",
    ): UserSignUpRequest {
        return UserSignUpRequest(
            name,
            gender,
            birthDate,
            email,
        )
    }
}

package com.loopers.domain.user.fixture

import com.loopers.domain.user.User
import com.loopers.domain.user.User.Gender.MALE
import com.loopers.infrastructure.user.UserJpaRepository

object UserServiceIntegrationFixture {
    fun saveUser(
        userRepository: UserJpaRepository,
        name: String = "userName",
        gender: User.Gender = MALE,
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
}

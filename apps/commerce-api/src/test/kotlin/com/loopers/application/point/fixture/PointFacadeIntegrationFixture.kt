package com.loopers.application.point.fixture

import com.loopers.domain.user.User
import com.loopers.domain.user.User.Gender
import com.loopers.domain.user.User.Gender.MALE
import com.loopers.infrastructure.user.UserJpaRepository

object PointFacadeIntegrationFixture {
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
}

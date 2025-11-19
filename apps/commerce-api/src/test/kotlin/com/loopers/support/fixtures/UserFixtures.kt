package com.loopers.support.fixtures

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand

object UserFixtures {
    fun createUser(
        userId: String = "userId",
        email: String = "test@example.com",
        birthDate: String = "1990-01-01",
        gender: Gender = Gender.MALE,
    ): User {
        return User.signUp(
            UserCommand.SignUp(
                userId = userId,
                email = email,
                birthDate = birthDate,
                gender = gender,
            ),
        )
    }

    fun createUser(
        id: Long = 1L,
        userId: String = "userId",
        email: String = "test@example.com",
        birthDate: String = "1990-01-01",
        gender: Gender = Gender.MALE,
    ): User {
        return User.signUp(
            UserCommand.SignUp(
                userId = userId,
                email = email,
                birthDate = birthDate,
                gender = gender,
            ),
        ).withId(id)
    }
}

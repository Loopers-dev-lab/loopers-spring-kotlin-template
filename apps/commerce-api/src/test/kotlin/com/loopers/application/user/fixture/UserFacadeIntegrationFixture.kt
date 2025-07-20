package com.loopers.application.user.fixture

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserInfo
import com.loopers.domain.user.User.Gender
import com.loopers.domain.user.User.Gender.MALE

object UserFacadeIntegrationFixture {
    fun signUp(
        userFacade: UserFacade,
        name: String = "userName",
        gender: Gender = MALE,
        birthDate: String = "1990-01-01",
        email: String = "xx@yy.zz",
    ): UserInfo {
        return userFacade.signUp(
            givenSignUp(
                name,
                gender,
                birthDate,
                email,
            ),
        )
    }

    fun givenSignUp(
        userName: String = "userName",
        gender: Gender = MALE,
        birthDate: String = "1990-01-01",
        email: String = "xx@yy.zz",
    ): UserInfo.SignUp {
        return UserInfo.SignUp(
            userName,
            gender,
            birthDate,
            email,
        )
    }
}

package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun getMe(userName: String): UserInfo? {
        return userService.getMe(userName)
            ?.let { UserInfo.from(it) }
    }

    fun signUp(signUp: UserInfo.SignUp): UserInfo {
        return userService.create(signUp.toEntity())
            .let { UserInfo.from(it) }
    }
}

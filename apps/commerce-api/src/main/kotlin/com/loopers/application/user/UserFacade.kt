package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserResult
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun createUser(
        username: String,
        email: String,
        birthDate: String,
        gender: User.Gender,
    ): UserResult.UserInfoResult {
        userService.throwIfUsernameExists(username)
        return userService.createUser(
            username = username,
            email = email,
            birthDate = birthDate,
            gender = gender,
        ).let { UserResult.UserInfoResult.from(it) }
    }

    fun getUserByUsername(username: String): UserResult.UserInfoResult {
        return userService.findByUsername(username)
            ?.let { UserResult.UserInfoResult.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 아이디 [$username]")
    }
}

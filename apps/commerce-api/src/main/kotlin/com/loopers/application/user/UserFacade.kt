package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val service: UserService,
) {
    fun registerUser(userId: String, email: String, birth: String, gender: Gender): UserInfo {
        return service.registerUser(userId, email, birth, gender)
            .let { UserInfo.from(it) }
    }

    fun getUser(userId: String): UserInfo {
        return service.getUserByUserId(userId)
            ?.let { UserInfo.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "User not found")
    }

    fun getPoint(userId: String): Int {
        return service.getPointByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "User not found")
    }
}

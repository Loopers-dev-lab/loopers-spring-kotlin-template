package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun registerUser(
        userId: String,
        email: String,
        birthDate: String,
        gender: Gender,
    ): UserInfo {
        return userService.registerUser(userId, email, birthDate, gender)
            .let { UserInfo.from(it) }
    }

    fun getUserByUserId(userId: String): UserInfo {
        return userService.getUserByUserId(userId)
            ?.let { UserInfo.from(it) }
            ?: throw CoreException(
                ErrorType.NOT_FOUND,
                "존재하지 않는 유저입니다.",
            )
    }

    fun getPointByUserId(userId: String): Long {
        return userService.getPointByUserId(userId)
            ?: throw CoreException(
                ErrorType.NOT_FOUND,
                "존재하지 않는 유저입니다.",
            )
    }

    fun chargePoint(userId: String, amount: Long): Long {
        return userService.chargePoint(userId, amount)
    }
}

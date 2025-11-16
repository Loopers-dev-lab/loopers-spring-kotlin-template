package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
) {
    @Transactional
    fun registerUser(request: UserRegisterRequest): UserInfo {
        val user = userService.registerUser(
            name = request.name,
            email = request.email,
            gender = request.gender,
            birthDate = request.birthDate,
        )

        return UserInfo.from(user)
    }

    @Transactional(readOnly = true)
    fun getUserInfo(userId: Long): UserInfo {
        val user = userService.getUser(userId)
        return UserInfo.from(user)
    }
}

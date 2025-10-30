package com.loopers.application.user

import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class UserFacade(private val userService: UserService, private val pointService: PointService) {

    @Transactional
    fun registerUser(command: UserRegisterCommand): UserInfo {
        val user = UserModel(
            loginId = command.loginId,
            email = command.email,
            birthDate = command.birthDate,
            gender = command.gender,
        )

        val savedUser = userService.registerUser(user)
        pointService.createPoint(savedUser.id, balance = 0L)

        return UserInfo.from(savedUser)
    }

    fun getUser(loginId: String): UserInfo {
        val user = userService.getUser(loginId) ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        return UserInfo.from(user)
    }
}

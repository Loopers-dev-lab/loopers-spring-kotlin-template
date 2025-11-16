package com.loopers.application.user

import com.loopers.domain.point.PointService
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserResult
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
    private val pointService: PointService,
) {

    @Transactional
    fun signUp(command: UserCommand.SignUp): UserResult {
        val user = userService.signUp(command)
        pointService.init(user.id)
        return user.let { UserResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMyInfo(userId: String): UserResult {
        return userService.getMyInfo(userId)
            ?.let { UserResult.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다: $userId")
    }
}

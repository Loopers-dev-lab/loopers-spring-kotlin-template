package com.loopers.application.user

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
) {

    @Transactional
    fun signUp(command: UserCommand.SignUp): UserResult {
        return userService.signUp(command).let { UserResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMyInfo(userId: String): UserResult {
        return userService.getMyInfo(userId)
            ?.let { UserResult.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND)
    }
}

package com.loopers.application.user

import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
) {
    fun getUserBy(id: Long): UserV1Info.GetById {
        return userService.findUserBy(id)
            ?.let { UserV1Info.GetById.from(it) }
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 유저를 찾을 수 없습니다.")
    }

    @Transactional
    fun signUp(criteria: UserV1Criteria.SignUp): UserV1Info.SignUp {
        userService.signUp(criteria.to())
            .let { return UserV1Info.SignUp.from(it) }
    }
}

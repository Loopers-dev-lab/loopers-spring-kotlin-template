package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
) {
    @Transactional
    fun signUp(criteria: UserV1Criteria.SignUp): UserV1Info.SignUp {
        userService.signUp(criteria.to())
            .let { return UserV1Info.SignUp.from(it) }
    }
}

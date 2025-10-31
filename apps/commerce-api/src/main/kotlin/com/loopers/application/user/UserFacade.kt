package com.loopers.application.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val service: UserService,
) {
    fun registerUser(userId: String, email: String, birth: String, gender: Gender): UserInfo {
        return service.registerUser(userId, email, birth, gender)
            .let { UserInfo.from(it) }
    }
}

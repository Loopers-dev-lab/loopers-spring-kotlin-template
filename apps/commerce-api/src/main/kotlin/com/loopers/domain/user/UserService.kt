package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun registerUser(
        userId: String,
        email: String,
        birth: String,
        gender: Gender,
    ): User {
        if (userRepository.existsByUserId(userId)) throw CoreException(ErrorType.CONFLICT, "User already exists")

        return userRepository.save(User(userId, email, birth, gender))
    }
}

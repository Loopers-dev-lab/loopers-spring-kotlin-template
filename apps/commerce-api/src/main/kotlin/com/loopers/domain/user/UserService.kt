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

    fun getUserByUserId(userId: String): User? {
        return userRepository.findByUserId(userId)
    }

    fun getPointByUserId(userId: String): Int? {
        return userRepository.findByUserId(userId)?.point
    }

    fun chargePointByUserId(userId: String, point: Int): Int {
        val user = userRepository.findByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "user not found")

        user.chargePoint(point)
        return user.point
    }
}

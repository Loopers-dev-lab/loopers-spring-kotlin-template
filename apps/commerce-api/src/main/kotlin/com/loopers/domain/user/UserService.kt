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
        birthDate: String,
        gender: Gender,
    ): User {
        if (userRepository.existsByUserId(userId)) {
            throw CoreException(
                ErrorType.CONFLICT,
                "이미 가입된 ID 입니다.",
            )
        }

        val user = User(
            userId = userId,
            email = email,
            birthDate = birthDate,
            gender = gender,
        )

        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun getUserByUserId(userId: String): User? {
        return userRepository.findByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun getPointByUserId(userId: String): Long? {
        return userRepository.findByUserId(userId)?.point
    }

    @Transactional
    fun chargePoint(userId: String, amount: Long): Long {
        val user = userRepository.findByUserId(userId)
            ?: throw CoreException(
                ErrorType.NOT_FOUND,
                "존재하지 않는 유저입니다.",
            )

        user.chargePoint(amount)
        return user.point
    }
}

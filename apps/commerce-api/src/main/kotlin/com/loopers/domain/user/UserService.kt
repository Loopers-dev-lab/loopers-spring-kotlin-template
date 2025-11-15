package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    fun registerUser(
        name: String,
        email: String,
        gender: Gender,
        birthDate: LocalDate,
    ): User {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(email)) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "이미 사용 중인 이메일입니다: $email",
            )
        }

        val user = User(
            name = name,
            email = email,
            gender = gender,
            birthDate = birthDate,
        )

        return userRepository.save(user)
    }

    fun getUser(userId: Long): User {
        return userRepository.findById(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "사용자를 찾을 수 없습니다: $userId",
            )
    }
}

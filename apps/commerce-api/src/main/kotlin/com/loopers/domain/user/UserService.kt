package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class UserService(private val userRepository: UserRepository) {
    fun registerUser(user: UserModel): UserModel = try {
        userRepository.save(user)
    } catch (e: DataIntegrityViolationException) {
        throw CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.")
    }

    fun getUser(loginId: String): UserModel? = userRepository.findByLoginIdValue(loginId)
}

package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional
    fun signUp(command: UserCommand.SignUp): User {
        if (userRepository.findBy(command.userId) != null) {
            throw CoreException(ErrorType.CONFLICT)
        }
        val user = userRepository.save(User.create(command))
        return user
    }

    @Transactional(readOnly = true)
    fun getMyInfo(userId: String): User {
        return userRepository.findBy(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다: $userId")
    }
}

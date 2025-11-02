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
        if (userRepository.exist(command.userId)) {
            throw CoreException(ErrorType.CONFLICT)
        }

        return userRepository.save(User.create(command))
    }

    @Transactional(readOnly = true)
    fun getMyInfo(userId: String): User? {
        return userRepository.getBy(userId)
    }
}

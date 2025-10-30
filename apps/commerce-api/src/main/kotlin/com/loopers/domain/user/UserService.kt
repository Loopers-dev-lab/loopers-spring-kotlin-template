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
    fun signUp(command: UserCommand.SignUp): User {
        val existsByUsername = userRepository.existsBy(command.username)
        if (existsByUsername) {
            throw CoreException(ErrorType.CONFLICT, "이미 가입된 유저입니다.")
        }

        val user = User.signUp(command.username, command.birth, command.email, command.gender)

        return userRepository.save(user)
    }
}

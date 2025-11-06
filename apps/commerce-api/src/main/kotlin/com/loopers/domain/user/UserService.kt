package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.validation.ConstraintViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun findUserBy(id: Long): User? {
        return userRepository.findById(id)
    }

    @Transactional
    fun signUp(command: UserCommand.SignUp): User {
        val existsByUsername = userRepository.existsBy(command.username)
        if (existsByUsername) {
            throw CoreException(ErrorType.CONFLICT, "이미 가입된 유저입니다.")
        }

        val user = User.signUp(command.username, command.birth, command.email, command.gender)

        return runCatching {
            userRepository.save(user)
        }.getOrElse { exception ->
            when (exception) {
                is ConstraintViolationException ->
                    throw CoreException(ErrorType.BAD_REQUEST, exception.message)

                else -> throw exception
            }
        }
    }
}

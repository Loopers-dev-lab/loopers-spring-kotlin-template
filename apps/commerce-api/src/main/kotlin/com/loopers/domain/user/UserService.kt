package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    fun createUser(username: String, password: String, email: String, birthDate: String, gender: User.Gender): User {
        return try {
            userRepository.save(
                User.of(
                    username = username,
                    password = password,
                    email = email,
                    birthDate = birthDate,
                    gender = gender,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            log.info("Data integrity violation exception: {}", e.message)
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다. [$username]")
        }
    }

    fun throwIfUsernameExists(username: String) {
        userRepository.findByUsername(username)?.let {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다. [$username]")
        }
    }

    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }
}

package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class UserService(
    private val userRepository: UserRepository,
) {

    fun getMe(userName: String): User? {
        return userRepository.findByUserName(userName)
    }

     fun existsByUserName(userName: String) {
        userRepository.findByUserName(userName)
            ?.let {
                throw CoreException(errorType = ErrorType.CONFLICT, customMessage = "[userId = $userName] 이미 존재하는 유저ID 입니다.")
            }
    }

    @Transactional
    fun create(user: User): User {
        existsByUserName(user.userName.value)
        return userRepository.save(user)
    }
}

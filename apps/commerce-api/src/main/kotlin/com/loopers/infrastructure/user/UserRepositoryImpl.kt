package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun find(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)
    }

    override fun findByUserName(userName: String): User? {
        return userJpaRepository.findByUserName(userName)
    }

    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }
}

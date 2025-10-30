package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UserRdbRepository(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun existsBy(username: String): Boolean {
        return userJpaRepository.existsByUsername(username)
    }

    override fun findById(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)
    }

    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }
}

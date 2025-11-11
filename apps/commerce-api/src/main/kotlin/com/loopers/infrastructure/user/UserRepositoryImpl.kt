package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserId
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }

    override fun exist(userId: String): Boolean {
        return userJpaRepository.existsByUserId(UserId(userId))
    }

    override fun findBy(userId: String): User? {
        return userJpaRepository.findByUserId(UserId(userId))
    }
}

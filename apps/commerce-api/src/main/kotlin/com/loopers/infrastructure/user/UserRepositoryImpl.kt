package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserId
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }

    override fun findBy(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)
    }

    override fun findBy(userId: String): User? {
        return userJpaRepository.findByUserId(UserId(userId))
    }
}

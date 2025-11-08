package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findById(id: Long): User? = userJpaRepository.findByIdOrNull(id)

    override fun save(user: User): User = userJpaRepository.save(user)

    override fun existsByEmail(
        email: String,
    ): Boolean = userJpaRepository.existsByEmail(email)
}

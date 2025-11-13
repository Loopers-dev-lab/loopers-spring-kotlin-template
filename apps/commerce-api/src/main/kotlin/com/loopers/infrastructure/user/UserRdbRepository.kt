package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class UserRdbRepository(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    @Transactional(readOnly = true)
    override fun findById(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)
    }

    @Transactional(readOnly = true)
    override fun findByUsername(userName: String): User? {
        return userJpaRepository.findByUsername(userName)
    }

    @Transactional
    override fun save(user: User): User {
        return userJpaRepository.save(user)
    }
}

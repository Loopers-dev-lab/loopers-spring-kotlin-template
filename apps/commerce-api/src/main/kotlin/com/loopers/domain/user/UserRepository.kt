package com.loopers.domain.user

interface UserRepository {
    fun findById(id: Long): User?
    fun save(user: User): User
    fun existsByEmail(email: String): Boolean
}

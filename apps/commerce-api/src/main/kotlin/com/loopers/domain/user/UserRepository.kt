package com.loopers.domain.user

interface UserRepository {
    fun save(user: User): User
    fun findByUserId(userId: String): User?
    fun existsByUserId(userId: String): Boolean
}

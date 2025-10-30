package com.loopers.domain.user

interface UserRepository {
    fun existsBy(username: String): Boolean
    fun findById(id: Long): User?
    fun save(user: User): User
}

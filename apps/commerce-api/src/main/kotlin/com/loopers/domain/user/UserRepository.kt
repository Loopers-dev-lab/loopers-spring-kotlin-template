package com.loopers.domain.user

interface UserRepository {
    fun findById(id: Long): User?
    fun findByUsername(userName: String): User?
    fun save(user: User): User
}

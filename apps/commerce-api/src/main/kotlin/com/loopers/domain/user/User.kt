package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    userId: String,
    email: String,
    birth: String,
    gender: Gender,
) : BaseEntity() {
    var userId: String = userId
        protected set

    var email: String = email
        protected set

    var birth: String = birth
        protected set

    var gender: Gender = gender
        protected set

    var point: Int = 0

    init {
        if (!validateUserId(userId)) throw CoreException(ErrorType.BAD_REQUEST, "invalid user id")

        if (!validateEmail(email)) throw CoreException(ErrorType.BAD_REQUEST, "invalid email")

        if (!validateBirth(birth)) throw CoreException(ErrorType.BAD_REQUEST, "invalid birth")
    }

    fun chargePoint(point: Int): Int {
        if (point <= 0) throw CoreException(ErrorType.BAD_REQUEST, "invalid point")

        this.point += point
        return this.point
    }

    companion object {
        private fun validateUserId(userId: String): Boolean {
            val userIdValidator = Regex("^[A-Za-z0-9]{1,10}$")

            return userIdValidator.matches(userId)
        }

        private fun validateEmail(email: String): Boolean {
            val emailValidator = Regex("^[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*\\.[a-zA-Z]{2,3}$")

            return emailValidator.matches(email)
        }

        private fun validateBirth(birth: String): Boolean {
            val birthValidator = Regex("^\\d{4}-\\d{2}-\\d{2}$")

            return birthValidator.matches(birth)
        }
    }
}

enum class Gender {
    NONE,
    MALE,
    FEMALE,
    OTHER,
}

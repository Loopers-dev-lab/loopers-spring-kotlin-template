package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.validation.UserValidator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user")
class User protected constructor(
    userName: UserName,
    gender: Gender,
    birthDate: BirthDate,
    email: Email,
) : BaseEntity() {

    @Column(name = "user_name", nullable = false, unique = true)
    var userName: UserName = userName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    var gender: Gender = gender
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: BirthDate = birthDate
        protected set

    @Column(name = "email", nullable = false)
    var email: Email = email
        protected set

    companion object {
        fun create(userName: String, gender: Gender, birthDate: String, email: String): User {
            return User(UserName(userName), gender, BirthDate(birthDate), Email(email))
        }
    }

    enum class Gender {
        MALE,
        FEMALE,
    }

    @JvmInline
    value class UserName(
        val value: String,
    ) {
        init {
            UserValidator.validateUserName(value)
        }
    }

    @JvmInline
    value class BirthDate(
        val value: String,
    ) {
        init {
            UserValidator.validateBirthDate(value)
        }
    }

    @JvmInline
    value class Email(
        val value: String,
    ) {
        init {
            UserValidator.validateEmail(value)
        }
    }
}

package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uq_username", columnNames = ["username"])],
)
class User(
    username: String,
    birth: LocalDate,
    email: String,
    gender: Gender,
) : BaseEntity() {

    @Column(name = "username", nullable = false)
    var username: String = username
        private set

    @Column(name = "birth", nullable = false)
    var birth: LocalDate = birth
        private set

    @Column(name = "email", nullable = false)
    var email: String = email
        private set

    @Column(name = "gender", nullable = false)
    @Enumerated(EnumType.STRING)
    var gender: Gender = gender
        private set

    init {
        if (!username.matches(USERNAME_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "id는 영문 대소문자, 숫자만 가능합니다.")
        }

        if (!email.matches(EMAIL_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
        }
    }

    companion object {
        fun signUp(username: String, birth: String, email: String, gender: Gender): User {
            val parsedBirthDate = runCatching {
                LocalDate.parse(birth, DateTimeFormatter.ofPattern(BIRTH_DATE_FORMAT))
            }.getOrElse {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다.")
            }
            return User(username, parsedBirthDate, email, gender)
        }

        private val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+$".toRegex()
        private val USERNAME_PATTERN = "^[A-Za-z0-9]{1,10}$".toRegex()
        private const val BIRTH_DATE_FORMAT = "yyyy-MM-dd"
    }
}

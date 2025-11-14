package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_user")
class User(
    @Column(unique = true, length = 10)
    val username: String,

    @Column(nullable = false, length = 255)
    val password: String,

    val email: String,
    val birthDate: String,
    val gender: Gender,
) : BaseEntity() {

    init {
        validateUsername(username)
        validatePassword(password)
        validateEmail(email)
        validateBirthDate(birthDate)
    }

    companion object {
        fun of(username: String, password: String, email: String, birthDate: String, gender: Gender): User {
            return User(
                username = username,
                password = password,
                email = email,
                birthDate = birthDate,
                gender = gender,
            )
        }

        private val USERNAME_PATTERN = "^[A-Za-z0-9]{1,10}$".toRegex()
        private val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        private val BIRTH_DATE_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$".toRegex()
    }

    private fun validateUsername(username: String) {
        if (!username.matches(USERNAME_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "아이디는 영문 대소문자와 숫자로만 이루어진 1~10자리여야 합니다.")
        }
    }

    private fun validatePassword(password: String) {
        if (password.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.")
        }
    }

    private fun validateEmail(email: String) {
        if (!email.matches(EMAIL_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
        }
    }

    private fun validateBirthDate(birthDate: String) {
        if (!birthDate.matches(BIRTH_DATE_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
        }
    }

    enum class Gender(val description: String) {
        MALE("남성"),
        FEMALE("여성"),
    }
}

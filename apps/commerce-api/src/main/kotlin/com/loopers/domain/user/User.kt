package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Entity
@Table(name = "users")
class User(
    userId: String,
    email: String,
    birthDate: String,
    gender: Gender,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, unique = true, length = 10)
    var userId: String = userId
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = parseBirthDate(birthDate)
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    var gender: Gender = gender
        protected set

    @Column(name = "point", nullable = false)
    var point: Long = 0
        protected set

    init {
        validateUserId(userId)
        validateEmail(email)
    }

    companion object {
        private val USER_ID_REGEX = Regex("^[a-zA-Z0-9]{1,10}$")
        private val EMAIL_REGEX = Regex("^[^@]+@[^@]+\\.[^@]+$")
        private val BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        private fun validateUserId(userId: String) {
            if (!USER_ID_REGEX.matches(userId)) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "ID는 영문 및 숫자 10자 이내여야 합니다.",
                )
            }
        }

        private fun validateEmail(email: String) {
            if (!EMAIL_REGEX.matches(email)) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "이메일은 xx@yy.zz 형식이어야 합니다.",
                )
            }
        }

        private fun parseBirthDate(birthDate: String): LocalDate {
            return try {
                LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER)
            } catch (e: DateTimeParseException) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "생년월일은 yyyy-MM-dd 형식이어야 합니다.",
                )
            }
        }
    }

    fun chargePoint(amount: Long) {
        if (amount <= 0) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "충전 금액은 0보다 커야 합니다.",
            )
        }
        this.point += amount
    }
}

enum class Gender {
    MALE,
    FEMALE,
    OTHER,
}

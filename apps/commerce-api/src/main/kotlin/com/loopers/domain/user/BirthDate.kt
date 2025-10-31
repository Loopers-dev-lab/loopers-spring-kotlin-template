package com.loopers.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Embeddable
data class BirthDate(
    @Column(name = "birth_date", nullable = false)
    val value: String,
) {
    init {
        validate(value)
    }

    private fun validate(birthDate: String) {
        require(birthDate.isNotBlank()) { ERROR_MESSAGE_BLANK }
        require(birthDate.matches(DATE_PATTERN)) { ERROR_MESSAGE_FORMAT }

        runCatching {
            LocalDate.parse(birthDate, FORMATTER)
        }.getOrElse {
            throw IllegalArgumentException("$ERROR_MESSAGE_INVALID: $birthDate")
        }
    }

    override fun toString(): String = value

    companion object {
        private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private const val ERROR_MESSAGE_BLANK = "birthDate는 필수입니다"
        private const val ERROR_MESSAGE_FORMAT = "birthDate는 yyyy-MM-dd 형식이어야 합니다"
        private const val ERROR_MESSAGE_INVALID = "유효하지 않은 날짜입니다"
    }
}

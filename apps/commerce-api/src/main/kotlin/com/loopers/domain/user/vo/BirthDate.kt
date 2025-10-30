package com.loopers.domain.user.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Embeddable
data class BirthDate(
    @Column(name = "birth_date")
    val value: LocalDate,
) {
    constructor(value: String) : this(parseDate(value))

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        private fun parseDate(dateString: String): LocalDate = try {
            LocalDate.parse(dateString, FORMATTER)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("생년월일은 yyyy-MM-dd 형식입니다.")
        }
    }
}

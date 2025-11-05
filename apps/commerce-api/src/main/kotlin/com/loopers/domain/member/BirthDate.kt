package com.loopers.domain.member

import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

@Embeddable
class BirthDate private constructor(
    @Column(name = "birth_date")
    val value: LocalDate
) {
    protected constructor() : this(LocalDate.now())

    companion object {
        fun from(value: String): BirthDate {
            return try {
                BirthDate(LocalDate.parse(value))
            } catch (_: Exception) {
                throw InvalidBirthDateException(ErrorType.BAD_REQUEST, "생년월일이 형식에 맞지 않습니다.")
            }
        }
    }
}

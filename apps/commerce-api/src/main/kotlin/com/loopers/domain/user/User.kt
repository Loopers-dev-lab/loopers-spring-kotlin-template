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

@Entity
@Table(name = "users")
class User(name: String, email: String, gender: Gender, birthDate: LocalDate) : BaseEntity() {
    @Column(nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(nullable = false, unique = true, length = 255)
    var email: String = email
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var gender: Gender = gender
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        }
        if (email.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.")
        }
        if (!email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.")
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.")
        }
    }
}

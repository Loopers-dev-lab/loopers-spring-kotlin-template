package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user")
class User(

    @Embedded
    val userId: UserId,

    @Embedded
    val email: Email,

    @Embedded
    val birthDate: BirthDate,

    @Column(nullable = false)
    @Enumerated(value = STRING)
    val gender: Gender,
) : BaseEntity() {

    companion object {

        fun create(command: UserCommand.SignUp): User {
            return User(
                userId = UserId(command.userId),
                email = Email(command.email),
                birthDate = BirthDate(command.birthDate),
                gender = command.gender,
            )
        }
    }
}

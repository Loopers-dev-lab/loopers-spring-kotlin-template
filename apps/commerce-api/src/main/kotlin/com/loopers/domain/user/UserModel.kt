package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Email
import com.loopers.domain.user.vo.Gender
import com.loopers.domain.user.vo.LoginId
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserModel(loginId: String, email: String, birthDate: String, gender: String) : BaseEntity() {

    @Embedded
    @Column(unique = true)
    var loginId: LoginId = LoginId(loginId)
        protected set

    @Embedded
    var email: Email = Email(email)
        protected set

    @Embedded
    var birthDate: BirthDate = BirthDate(birthDate)
        protected set

    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.from(gender)
        protected set
}

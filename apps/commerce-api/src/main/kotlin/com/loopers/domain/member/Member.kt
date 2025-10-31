package com.loopers.domain.member

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Email
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.NaturalId

@Entity
@Table(name = "members")
class Member(
    @Embedded
    @NaturalId
    val memberId: MemberId,
    @Embedded
    val email: Email,
    @Embedded
    val birthDate: BirthDate,
    @Enumerated(EnumType.STRING)
    val gender: Gender,
) : BaseEntity() {

    @Embedded
    var point: Point = Point(0L)
        protected set

    fun chargePoint(amount: Long) {
        point = Point(point.amount + amount)
    }

}

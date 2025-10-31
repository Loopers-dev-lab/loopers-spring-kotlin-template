package com.loopers.application.member

import com.loopers.domain.member.Member


data class MemberInfo(
    val id: Long,
    val memberId: String,
    val email: String,
    val birthDate: String,
    val gender: String,
    val point: Long,
) {
    companion object {
        fun from(member: Member): MemberInfo {
            return MemberInfo(
                id = member.id,
                memberId = member.memberId.value,
                email = member.email.address,
                birthDate = member.birthDate.value.toString(),
                gender = member.gender.name,
                point = member.point.amount,
            )
        }
    }
}

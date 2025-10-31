package com.loopers.interfaces.api.point

import com.loopers.application.member.MemberInfo

class PointV1Dto {
    data class MemberResponse(
        val id: Long,
        val memberId: String,
        val email: String,
        val birthDate: String,
        val gender: String,
        val point: Long,
    ) {
        companion object {
            fun from(memberInfo: MemberInfo): MemberResponse {
                return MemberResponse(
                    id = memberInfo.id,
                    memberId = memberInfo.memberId,
                    email = memberInfo.email,
                    birthDate = memberInfo.birthDate,
                    gender = memberInfo.gender,
                    point = memberInfo.point,
                )
            }
        }
    }

    data class PointResponse(
        val point: Long,
    )

    data class ChargePointRequest(
        val amount: Long,
    )
}

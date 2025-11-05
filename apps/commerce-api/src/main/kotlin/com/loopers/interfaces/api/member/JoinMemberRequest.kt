package com.loopers.interfaces.api.member

import com.loopers.application.member.JoinMemberCommand
import jakarta.validation.constraints.NotNull

data class JoinMemberRequest(
    val memberId: String,
    val email: String,
    val birthDate: String,
    @field:NotNull(message = "성별은 필수 입력값입니다")
    var gender: String?,
) {
    fun toCommand(): JoinMemberCommand {
        return JoinMemberCommand(
            memberId = memberId,
            email = email,
            birthDate = birthDate,
            gender = gender!!,
        )
    }
}

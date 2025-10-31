package com.loopers.application.member

data class JoinMemberCommand(
    val memberId: String,
    val email: String,
    val birthDate: String,
    val gender: String,
)

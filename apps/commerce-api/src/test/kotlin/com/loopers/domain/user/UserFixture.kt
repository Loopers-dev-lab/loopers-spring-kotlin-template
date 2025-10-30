package com.loopers.domain.user

object UserFixture {
    private const val DEFAULT_LOGIN_ID = "sonjs7554"
    private const val DEFAULT_EMAIL = "sonjs7554@naver.com"
    private const val DEFAULT_BIRTH_DATE = "1994-03-12"
    private const val DEFAULT_GENDER = "MALE"

    fun create(
        loginId: String = DEFAULT_LOGIN_ID,
        email: String = DEFAULT_EMAIL,
        birthDate: String = DEFAULT_BIRTH_DATE,
        gender: String = DEFAULT_GENDER,
    ): UserModel = UserModel(
            loginId = loginId,
            email = email,
            birthDate = birthDate,
            gender = gender,
        )
}

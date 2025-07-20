package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UserTest {
    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidIdCases")
    fun `ID 형식이 유효하지 않으면 예외가 발생한다`(userName: String) {
        assertCreateFails(userName = userName)
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidEmailCases")
    fun `이메일 형식이 유효하지 않으면 예외가 발생한다`(email: String) {
        assertCreateFails(email = email)
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidBirthDateCases")
    fun `생년월일 형식이 유효하지 않으면 예외가 발생한다`(birthDate: String) {
        assertCreateFails(birthDate = birthDate)
    }

    // ✨ 공통 로직으로 추출
    private fun assertCreateFails(
        userName: String = "userName",
        gender: User.Gender = User.Gender.MALE,
        birthDate: String = "1990-01-01",
        email: String = "xx@yy.zz",
    ) {
        val result = assertThrows<CoreException> {
            User.create(userName, gender, birthDate, email)
        }
        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    companion object {
        @JvmStatic
        fun invalidBirthDateCases() = listOf(
            Arguments.of("999-99-99-99", "형식이 완전 엉망인 날짜"),
            Arguments.of("999999-99", "구분자가 없고 길이도 부족함"),
            Arguments.of("9999-9999", "달, 일이 4자리"),
            Arguments.of("99999999", "구분자가 전혀 없음"),
            Arguments.of("9999-999-99", "달이 세자리"),
            Arguments.of("9999-99-909", "일이 세자리"),
        )

        @JvmStatic
        fun invalidEmailCases() = listOf(
            Arguments.of("xxyy.zz", "도메인에 '@' 없음"),
            Arguments.of("xx@yyzz", "도메인에 '.' 없음"),
            Arguments.of("xx@@yy.zz", "'@' 중복"),
            Arguments.of("@yy.zz", "아이디 없음"),
            Arguments.of("xx@.zz", "도메인 시작이 '.'"),
            Arguments.of("xx@yy.", "도메인 끝이 '.'"),
        )

        @JvmStatic
        fun invalidIdCases() = listOf(
            Arguments.of("a한글이껴있네1A", "한글이 섞인 ID"),
            Arguments.of("한글이야", "전부 한글"),
            Arguments.of("", "빈 문자열"),
            Arguments.of("12345678910", "숫자 길이 초과 (10자 이상)"),
        )
    }
}

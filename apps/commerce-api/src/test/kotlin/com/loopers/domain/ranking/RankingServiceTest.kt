package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@DisplayName("RankingService 테스트")
class RankingServiceTest {

    private val rankingRepository: RankingRepository = mockk()
    private val rankingService = RankingService(rankingRepository)

    @Nested
    @DisplayName("parseDateKey 메서드는")
    inner class ParseDateKey {

        @Test
        fun testParseDateKeyWhenValidThenReturnsFormattedKey() {
            // given
            val date = "20250102"

            // when
            val dateKey = rankingService.parseDateKey(date)

            // then
            assertThat(dateKey).isEqualTo("20250102")
        }

        @Test
        fun testParseDateKeyWhenInvalidThenThrowsBadRequest() {
            // given
            val date = "2025-01-02"

            // when & then
            assertThatThrownBy { rankingService.parseDateKey(date) }
                .isInstanceOf(CoreException::class.java)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @Nested
    @DisplayName("todayDateKey 메서드는")
    inner class TodayDateKey {

        @Test
        fun testTodayDateKeyWhenCalledThenReturnsKoreaDateKey() {
            // given
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val expected = LocalDate.now(ZoneId.of("Asia/Seoul")).format(formatter)

            // when
            val dateKey = rankingService.todayDateKey()

            // then
            assertThat(dateKey).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("getPagedScores 메서드는")
    inner class GetPagedScores {

        @Test
        fun testGetPagedScoresWhenValidThenReturnsSortedScores() {
            // given
            val dateKey = "20250102"
            val offset = 0L
            val size = 3
            val scores = listOf(
                RankingScore(productId = 2L, score = 10.0),
                RankingScore(productId = 1L, score = 10.0),
                RankingScore(productId = 3L, score = 5.0),
            )

            every { rankingRepository.getScores(dateKey, offset, 2L) } returns scores

            // when
            val result = rankingService.getPagedScores(dateKey, offset, size)

            // then
            assertThat(result.map { it.productId }).containsExactly(2L, 1L, 3L)
            verify(exactly = 1) { rankingRepository.getScores(dateKey, offset, 2L) }
        }

        @Test
        fun testGetPagedScoresWhenOffsetIsNegativeThenReturnsEmpty() {
            // when
            val result = rankingService.getPagedScores("20250102", -1L, 10)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { rankingRepository.getScores(any(), any(), any()) }
        }

        @Test
        fun testGetPagedScoresWhenSizeIsZeroThenReturnsEmpty() {
            // when
            val result = rankingService.getPagedScores("20250102", 0L, 0)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 0) { rankingRepository.getScores(any(), any(), any()) }
        }
    }
}

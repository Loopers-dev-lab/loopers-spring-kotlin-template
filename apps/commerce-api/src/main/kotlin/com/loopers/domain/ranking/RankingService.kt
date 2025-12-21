package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class RankingService(
    private val rankingRepository: RankingRepository,
) {
    companion object {
        private val DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    fun parseDateKey(date: String): String {
        return try {
            LocalDate.parse(date, DATE_KEY_FORMATTER).format(DATE_KEY_FORMATTER)
        } catch (e: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "랭킹 날짜 형식이 올바르지 않습니다: $date")
        }
    }

    fun todayDateKey(): String = LocalDate.now().format(DATE_KEY_FORMATTER)

    fun getPagedScores(dateKey: String, offset: Long, size: Int): List<RankingScore> {
        if (offset < 0 || size <= 0) {
            return emptyList()
        }

        val end = offset + size - 1L
        if (end < offset) {
            return emptyList()
        }

        val scores = rankingRepository.getScores(dateKey, offset, end)
        if (scores.isEmpty()) {
            return emptyList()
        }

        return scores.sortedWith(
            compareByDescending<RankingScore> { it.score }
                .thenByDescending { it.productId },
        )
    }

    fun getScore(dateKey: String, productId: Long): Double? = rankingRepository.getScore(dateKey, productId)

    fun getTotalCount(dateKey: String): Long = rankingRepository.getTotalCount(dateKey)

    fun getRank(dateKey: String, productId: Long): Long? = rankingRepository.getRank(dateKey, productId)
}

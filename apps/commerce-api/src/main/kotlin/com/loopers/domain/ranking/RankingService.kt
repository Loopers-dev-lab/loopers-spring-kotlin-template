package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

@Service
class RankingService(
    private val rankingRepository: RankingRepository,
    private val productWeeklyRankingRepository: ProductWeeklyRankingRepository,
    private val productMonthlyRankingRepository: ProductMonthlyRankingRepository,
) {
    companion object {
        private val DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    fun parseDateKey(date: String): String = parseDate(date).format(DATE_KEY_FORMATTER)

    fun parseDate(date: String): LocalDate {
        return try {
            LocalDate.parse(date, DATE_KEY_FORMATTER)
        } catch (e: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "랭킹 날짜 형식이 올바르지 않습니다: $date")
        }
    }

    fun todayDateKey(): String = LocalDate.now().format(DATE_KEY_FORMATTER)

    fun getRankingScores(period: RankingPeriod, date: String, pageable: Pageable): Page<RankingScore> {
        val parsedDate = parseDate(date)

        return when (period) {
            RankingPeriod.DAILY -> getDailyScores(parsedDate, pageable)
            RankingPeriod.WEEKLY -> getWeeklyScores(parsedDate, pageable)
            RankingPeriod.MONTHLY -> getMonthlyScores(parsedDate, pageable)
        }
    }

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

    private fun getDailyScores(date: LocalDate, pageable: Pageable): Page<RankingScore> {
        val dateKey = date.format(DATE_KEY_FORMATTER)
        val totalCount = getTotalCount(dateKey)

        if (totalCount == 0L) {
            return Page.empty(pageable)
        }

        if (pageable.offset >= totalCount) {
            return PageImpl(emptyList(), pageable, totalCount)
        }

        val pageScores = getPagedScores(dateKey, pageable.offset, pageable.pageSize)
        if (pageScores.isEmpty()) {
            return PageImpl(emptyList(), pageable, totalCount)
        }

        return PageImpl(pageScores, pageable, totalCount)
    }

    private fun getWeeklyScores(date: LocalDate, pageable: Pageable): Page<RankingScore> {
        val (weekStart, weekEnd) = weekRange(date)
        val page = productWeeklyRankingRepository.findByWeekRange(weekStart, weekEnd, pageable)

        return page.map { RankingScore(it.productId, it.score) }
    }

    private fun getMonthlyScores(date: LocalDate, pageable: Pageable): Page<RankingScore> {
        val monthPeriod = YearMonth.from(date)
        val page = productMonthlyRankingRepository.findByMonthPeriod(monthPeriod, pageable)

        return page.map { RankingScore(it.productId, it.score) }
    }

    private fun weekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return start to end
    }
}

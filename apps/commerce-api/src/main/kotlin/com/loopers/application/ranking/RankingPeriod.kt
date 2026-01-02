package com.loopers.application.ranking

import com.loopers.support.DateUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RankingPeriod {
    DAILY {
        override fun formatDate(date: String?): String =
            date ?: LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

        override fun getRankings(
            date: String?,
            pageable: Pageable,
            facade: RankingFacade
        ): Page<RankingInfo> =
            facade.getRankings(formatDate(date), pageable)
    },
    WEEKLY {
        override fun formatDate(date: String?): String =
            date ?: DateUtils.toYearWeek(LocalDate.now())

        override fun getRankings(
            date: String?,
            pageable: Pageable,
            facade: RankingFacade
        ): Page<RankingInfo> =
            facade.getWeeklyRankings(formatDate(date), pageable)
    },
    MONTHLY {
        override fun formatDate(date: String?): String =
            date ?: DateUtils.toYearMonth(LocalDate.now())

        override fun getRankings(
            date: String?,
            pageable: Pageable,
            facade: RankingFacade
        ): Page<RankingInfo> =
            facade.getMonthlyRankings(formatDate(date), pageable)
    };

    abstract fun formatDate(date: String?): String
    abstract fun getRankings(
        date: String?,
        pageable: Pageable,
        facade: RankingFacade
    ): Page<RankingInfo>

    companion object {
        fun from(period: String): RankingPeriod = try {
            valueOf(period.uppercase())
        } catch (e: IllegalArgumentException) {
            DAILY
        }
    }
}

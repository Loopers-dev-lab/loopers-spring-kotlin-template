package com.loopers.application.ranking

import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

class RankingCommand {

    data class GetRankings(
        val pageable: Pageable,
        val date: LocalDateTime,
    ) {
        companion object {
            fun toCommand(pageable: Pageable, date: LocalDateTime): GetRankings = GetRankings(pageable, date)
        }
    }
}

package com.loopers.interfaces.api.dto

/**
 * Request DTO for manual batch execution.
 * Based on api-design-weekly-monthly-ranking.md#2.2
 *
 * @property baseDate Aggregation base date. yyyyMMddHH for hourly, yyyyMMdd for others. Optional, defaults to today.
 */
data class BatchExecutionRequest(
    val baseDate: String? = null,
)

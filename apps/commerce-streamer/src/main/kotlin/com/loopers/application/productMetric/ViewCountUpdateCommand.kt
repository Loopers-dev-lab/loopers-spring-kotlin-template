package com.loopers.application.productMetric

data class ViewCountUpdateCommand(val viewCountGroupBy: Map<Long, Long>)

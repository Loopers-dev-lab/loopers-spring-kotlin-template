package com.loopers.domain.product.policy

import com.loopers.support.cache.CachePolicy
import java.time.Duration

object ProductCachePolicy : CachePolicy {
    override val namespace: String = "product"

    override fun getTtl(kind: String): Duration = when (kind) {
        "list" -> Duration.ofMinutes(3)
        "count" -> Duration.ofMinutes(5)
        else -> Duration.ofMinutes(3)
    }
}

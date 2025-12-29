package com.loopers

import com.loopers.testcontainers.MySqlTestContainersConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * CommerceBatch Application Context 로딩 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig::class)
class CommerceBatchApplicationTest @Autowired constructor(
    private val applicationContext: ApplicationContext,

    ) {
    @Test
    fun `Application Context가 정상적으로 로드된다`() {
        // then: Context가 정상적으로 로드되면 테스트 통과
    }

    @Test
    fun `필수 Bean들이 등록되어 있다`() {
        // then: Batch Job 관련 Bean들이 등록되어 있어야 함
        assert(applicationContext.containsBean("weeklyRankingAggregationJob"))
        assert(applicationContext.containsBean("monthlyRankingAggregationJob"))
    }
}

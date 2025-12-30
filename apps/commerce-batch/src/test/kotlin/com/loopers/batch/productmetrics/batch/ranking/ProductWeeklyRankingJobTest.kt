package com.loopers.batch.productmetrics.batch.ranking

import com.loopers.IntegrationTest
import com.loopers.batch.productmetrics.ranking.ProductWeeklyRankingJobConfig
import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.ranking.ProductWeeklyRankingRepository
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import java.time.LocalDate

@DisplayName("ProductWeeklyRankingJob 통합 테스트")
class ProductWeeklyRankingJobTest : IntegrationTest() {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    @Qualifier(ProductWeeklyRankingJobConfig.JOB_NAME)
    private lateinit var job: Job

    @Autowired
    private lateinit var productMetricsJpaRepository: ProductMetricsJpaRepository

    @Autowired
    private lateinit var rankingRepository: ProductWeeklyRankingRepository

    private val weekEnd = LocalDate.now().minusDays(1)
    private val weekStart = weekEnd.minusDays(6)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
    }

    @Test
    @DisplayName("전체 배치 흐름이 정상적으로 동작한다")
    fun testCompleteJobExecution() {
        // given: 10개 상품의 주간 데이터 준비
        // 점수 계산: (view * 0.1 + like * 0.2 + sold * 0.7) * 날짜별 감쇠 가중치
        val yesterday = LocalDate.now().minusDays(1)

        (1L..10L).forEach { productId ->
            (0..6).forEach { daysAgo ->
                val date = yesterday.minusDays(daysAgo.toLong())
                if (date in weekStart..weekEnd) {
                    val metrics = ProductMetrics.create(productId, date)
                    metrics.viewCount = (productId * 100)
                    metrics.likeCount = (productId * 50)
                    metrics.soldCount = (productId * 20)
                    productMetricsJpaRepository.save(metrics)
                }
            }
        }

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis()) // 고유성 보장
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then: Job 성공
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        // 랭킹 데이터 검증
        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        assertThat(rankings).isNotEmpty
        assertThat(rankings).hasSizeLessThanOrEqualTo(100)

        // 순위가 정상적으로 매겨졌는지 확인
        rankings.forEach { ranking ->
            assertThat(ranking.ranking).isGreaterThan(0)
            assertThat(ranking.score).isGreaterThanOrEqualTo(0.0)
        }

        // 순위가 점수 내림차순인지 확인
        val sortedByRanking = rankings.sortedBy { it.ranking }
        for (i in 0 until sortedByRanking.size - 1) {
            assertThat(sortedByRanking[i].score).isGreaterThanOrEqualTo(sortedByRanking[i + 1].score)
        }

        // 가중치 적용 시 productId가 클수록 점수가 높음
        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1).isNotNull
        assertThat(top1!!.productId).isEqualTo(10L)
    }

    @Test
    @DisplayName("150개 상품 중 Top 100만 저장한다")
    fun testSavesOnlyTop100() {
        // given: 150개 상품 데이터
        val yesterday = LocalDate.now().minusDays(1)

        (1L..150L).forEach { productId ->
            val metrics = ProductMetrics.create(productId, yesterday)
            metrics.viewCount = ((150 - productId) * 100)
            metrics.likeCount = (150 - productId)
            metrics.soldCount = ((150 - productId) / 10)
            productMetricsJpaRepository.save(metrics)
        }

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        assertThat(rankings).hasSize(100)

        // 1위는 productId=1 (가장 높은 점수)
        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1).isNotNull
        assertThat(top1!!.productId).isEqualTo(1L)

        // productId 101~150은 저장되지 않아야 함
        val excludedProducts = rankings.filter { it.productId > 100 }
        assertThat(excludedProducts).isEmpty()
    }

    @Test
    @DisplayName("여러 번 실행해도 Top 100만 유지된다")
    fun testMultipleExecutionsKeepTop100() {
        // given: 첫 번째 실행용 데이터 (50개)
        val yesterday = LocalDate.now().minusDays(1)

        (1L..50L).forEach { productId ->
            val metrics = ProductMetrics.create(productId, yesterday)
            metrics.viewCount = (productId * 100)
            metrics.likeCount = productId
            metrics.soldCount = (productId / 10)
            productMetricsJpaRepository.save(metrics)
        }

        // when: 첫 번째 Job 실행
        val jobParameters1 = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution1 = jobLauncherTestUtils.launchJob(jobParameters1)
        assertThat(execution1.status).isEqualTo(BatchStatus.COMPLETED)

        // 추가 데이터 저장 (51~150)
        (51L..150L).forEach { productId ->
            val metrics = ProductMetrics.create(productId, yesterday)
            metrics.viewCount = (productId * 100)
            metrics.likeCount = productId
            metrics.soldCount = (productId / 10)
            productMetricsJpaRepository.save(metrics)
        }

        // when: 두 번째 Job 실행
        val jobParameters2 = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters()

        val execution2 = jobLauncherTestUtils.launchJob(jobParameters2)
        assertThat(execution2.status).isEqualTo(BatchStatus.COMPLETED)

        // then: 여전히 100개만 저장되어 있어야 함
        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        assertThat(rankings).hasSize(100)

        // Top 100은 productId 51~150 중에서 선택되어야 함 (점수가 더 높으므로)
        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1!!.productId).isEqualTo(150L) // 가장 높은 점수
    }

    @Test
    @DisplayName("날짜별 감쇠 가중치가 적용된다")
    fun testAppliesDecayWeights() {
        // given: 두 상품의 서로 다른 날짜 데이터
        // weekEnd가 기준일 (yesterday = weekEnd)
        val productId1 = 1L
        val productId2 = 2L

        // productId1: weekEnd (D+0, 감쇠 가중치 1.0)
        val metricsD0 = ProductMetrics.create(productId1, weekEnd)
        metricsD0.viewCount = 100
        metricsD0.likeCount = 10
        metricsD0.soldCount = 5
        productMetricsJpaRepository.save(metricsD0)

        // productId2: weekStart (D+6, 감쇠 가중치 0.1) - 같은 메트릭 값
        val metricsD6 = ProductMetrics.create(productId2, weekStart)
        metricsD6.viewCount = 100
        metricsD6.likeCount = 10
        metricsD6.soldCount = 5
        productMetricsJpaRepository.save(metricsD6)

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        val ranking1 = rankings.find { it.productId == productId1 }
        val ranking2 = rankings.find { it.productId == productId2 }

        assertThat(ranking1).isNotNull
        assertThat(ranking2).isNotNull

        // D+0 (감쇠 1.0)의 점수가 D+6 (감쇠 0.1)보다 약 10배 높아야 함
        assertThat(ranking1!!.score).isGreaterThan(ranking2!!.score)

        // 실제 점수 비율 확인 (약 10배 차이)
        val ratio = ranking1.score / ranking2.score
        assertThat(ratio).isBetween(9.0, 11.0)
    }

    @Test
    @DisplayName("데이터가 없을 때도 정상 종료된다")
    fun testCompletesSuccessfullyWithNoData() {
        // given: 데이터 없음

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then: 정상 종료
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        // 랭킹 데이터 없음
        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        assertThat(rankings).isEmpty()
    }

    @Test
    @DisplayName("weekStart와 weekEnd가 정확하게 저장된다")
    fun testStoresCorrectWeekRange() {
        // given: 테스트 데이터
        val yesterday = LocalDate.now().minusDays(1)
        val metrics = ProductMetrics.create(1L, yesterday)
        metrics.viewCount = 100
        metrics.likeCount = 50
        metrics.soldCount = 20
        productMetricsJpaRepository.save(metrics)

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("weekStart", weekStart.toString())
            .addString("weekEnd", weekEnd.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = rankingRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd)
        rankings.forEach { ranking ->
            assertThat(ranking.weekStart).isEqualTo(weekStart)
            assertThat(ranking.weekEnd).isEqualTo(weekEnd)
        }
    }
}

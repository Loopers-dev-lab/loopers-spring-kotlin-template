package com.loopers.batch.productmetrics.batch.ranking

import com.loopers.IntegrationTest
import com.loopers.batch.productmetrics.ranking.ProductMonthlyRankingJobConfig
import com.loopers.domain.metrics.ProductMetrics
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository
import com.loopers.domain.ranking.ProductMonthlyRankingRepository
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
import java.time.YearMonth

@DisplayName("ProductMonthlyRankingJob 통합 테스트")
class ProductMonthlyRankingJobTest : IntegrationTest() {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    @Qualifier(ProductMonthlyRankingJobConfig.JOB_NAME)
    private lateinit var job: Job

    @Autowired
    private lateinit var monthlyRankingRepository: ProductMonthlyRankingRepository

    @Autowired
    private lateinit var productMetricsJpaRepository: ProductMetricsJpaRepository

    private val monthPeriod = YearMonth.now()
    private val monthStart = monthPeriod.atDay(1)

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
    }

    @Test
    @DisplayName("전체 배치 흐름이 정상적으로 동작한다")
    fun testCompleteJobExecution() {
        // given: 10개 상품의 월간 메트릭 데이터 준비 (2일)
        // 점수 계산: view * 0.1 + like * 0.2 + sold * 0.7
        val firstDay = monthStart
        val secondDay = monthStart.plusDays(7)

        val metrics = mutableListOf<ProductMetrics>()
        (1L..10L).forEach { productId ->
            val day1 = ProductMetrics.create(productId, firstDay).apply {
                viewCount = productId * 10
                likeCount = productId * 5
                soldCount = productId * 2
            }
            val day2 = ProductMetrics.create(productId, secondDay).apply {
                viewCount = productId * 5
                likeCount = productId * 3
                soldCount = productId
            }
            metrics.add(day1)
            metrics.add(day2)
        }
        productMetricsJpaRepository.saveAll(metrics)

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then: Job 성공
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = monthlyRankingRepository.findByMonthPeriod(monthPeriod)
        assertThat(rankings).isNotEmpty
        assertThat(rankings).hasSizeLessThanOrEqualTo(100)

        rankings.forEach { ranking ->
            assertThat(ranking.ranking).isGreaterThan(0)
            assertThat(ranking.score).isGreaterThanOrEqualTo(0.0)
        }

        val sortedByRanking = rankings.sortedBy { it.ranking }
        for (i in 0 until sortedByRanking.size - 1) {
            assertThat(sortedByRanking[i].score).isGreaterThanOrEqualTo(sortedByRanking[i + 1].score)
        }

        // 가중치 적용 시 productId가 클수록 점수가 높음 (sold가 가장 큰 가중치)
        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1).isNotNull
        assertThat(top1!!.productId).isEqualTo(10L)
    }

    @Test
    @DisplayName("150개 상품 중 Top 100만 저장한다")
    fun testSavesOnlyTop100() {
        // given: 150개 상품 월간 메트릭 데이터
        // 점수가 높은 순으로: productId 1이 가장 높음
        val metrics = (1L..150L).map { productId ->
            ProductMetrics.create(productId, monthStart).apply {
                viewCount = (150 - productId) * 10
                likeCount = (150 - productId) * 5
                soldCount = (150 - productId) * 2
            }
        }
        productMetricsJpaRepository.saveAll(metrics)

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = monthlyRankingRepository.findByMonthPeriod(monthPeriod)
        assertThat(rankings).hasSize(100)

        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1).isNotNull
        assertThat(top1!!.productId).isEqualTo(1L)

        val excludedProducts = rankings.filter { it.productId > 100 }
        assertThat(excludedProducts).isEmpty()
    }

    @Test
    @DisplayName("여러 번 실행해도 Top 100만 유지된다")
    fun testMultipleExecutionsKeepTop100() {
        // given: 첫 번째 실행용 데이터 (50개)
        val firstBatch = (1L..50L).map { productId ->
            ProductMetrics.create(productId, monthStart).apply {
                viewCount = productId * 10
                likeCount = productId * 5
                soldCount = productId * 2
            }
        }
        productMetricsJpaRepository.saveAll(firstBatch)

        // when: 첫 번째 Job 실행
        val jobParameters1 = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution1 = jobLauncherTestUtils.launchJob(jobParameters1)
        assertThat(execution1.status).isEqualTo(BatchStatus.COMPLETED)

        // 추가 데이터 저장 (51~150) - 더 높은 점수
        val secondBatch = (51L..150L).map { productId ->
            ProductMetrics.create(productId, monthStart.plusDays(7)).apply {
                viewCount = productId * 100
                likeCount = productId * 50
                soldCount = productId * 20
            }
        }
        productMetricsJpaRepository.saveAll(secondBatch)

        // when: 두 번째 Job 실행
        val jobParameters2 = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters()

        val execution2 = jobLauncherTestUtils.launchJob(jobParameters2)
        assertThat(execution2.status).isEqualTo(BatchStatus.COMPLETED)

        // then: 여전히 100개만 저장되어 있어야 함
        val rankings = monthlyRankingRepository.findByMonthPeriod(monthPeriod)
        assertThat(rankings).hasSize(100)

        val top1 = rankings.find { it.ranking == 1 }
        assertThat(top1!!.productId).isEqualTo(150L)
    }

    @Test
    @DisplayName("데이터가 없을 때도 정상 종료된다")
    fun testCompletesSuccessfullyWithNoData() {
        // given: 데이터 없음

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then: 정상 종료
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = monthlyRankingRepository.findByMonthPeriod(monthPeriod)
        assertThat(rankings).isEmpty()
    }

    @Test
    @DisplayName("monthPeriod가 정확하게 저장된다")
    fun testStoresCorrectMonthPeriod() {
        // given: 테스트 데이터
        productMetricsJpaRepository.save(
            ProductMetrics.create(1L, monthStart).apply {
                viewCount = 100
                likeCount = 50
                soldCount = 20
            },
        )

        // when: Job 실행
        val jobParameters = JobParametersBuilder()
            .addString("yearMonth", monthPeriod.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // then
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val rankings = monthlyRankingRepository.findByMonthPeriod(monthPeriod)
        rankings.forEach { ranking ->
            assertThat(ranking.monthPeriod).isEqualTo(monthPeriod)
        }
    }
}

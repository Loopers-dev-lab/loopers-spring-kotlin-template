package com.loopers.job.demo

import com.loopers.batch.job.demo.DemoJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${DemoJobConfig.JOB_NAME}"])
class DemoJobE2ETest @Autowired constructor(
    // IDE 정적 분석 상 [SpringBatchTest] 의 주입보다 [SpringBootTest] 의 주입이 우선되어, 해당 컴포넌트는 없으므로 오류처럼 보일 수 있음.
    // [SpringBatchTest] 자체가 Scope 기반으로 주입하기 때문에 정상 동작함.
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(DemoJobConfig.JOB_NAME) private val job: Job,
) {
    @BeforeEach
    fun beforeEach() {
    }

    @DisplayName("jobParameter 중 requestDate 인자가 주어지지 않았을 때, demoJob 배치는 실패한다.")
    @Test
    fun shouldFail_whenJobParameterNotFound() {
        // arrange
        jobLauncherTestUtils.job = job

        // act
        val jobExecution = jobLauncherTestUtils.launchJob()

        // assert
        assertAll(
            { assertThat(jobExecution).isNotNull() },
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode) },
        )
    }

    @DisplayName("demoJob 배치가 정상적으로 실행된다.")
    @Test
    fun success() {
        // arrange
        jobLauncherTestUtils.job = job

        // act
        val jobParameters = JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .toJobParameters()
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertAll(
            { assertThat(jobExecution).isNotNull() },
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
        )
    }
}

package com.loopers.support.config

import com.loopers.interfaces.point.PointApiFixture
import com.loopers.interfaces.user.UserApiFixture
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean

@TestConfiguration
class ApiFixtureConfig {

    @Bean
    fun userTestFixture(testRestTemplate: TestRestTemplate): UserApiFixture {
        return UserApiFixture(testRestTemplate)
    }

    @Bean
    fun pointApiFixture(testRestTemplate: TestRestTemplate): PointApiFixture {
        return PointApiFixture(testRestTemplate)
    }
}

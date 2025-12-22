package com.loopers

import com.loopers.testcontainers.RedisTestContainersConfig
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.test.context.TestConstructor

@SpringBootTest()
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Profile("test")
@Import(RedisTestContainersConfig::class)
abstract class IntegrationTestSupport

package com.loopers

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.test.context.TestConstructor

@SpringBootTest()
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Profile("test")
abstract class IntegrationTestSupport

package com.loopers

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile

@SpringBootTest
@Profile("test")
abstract class IntegrationTestSupport

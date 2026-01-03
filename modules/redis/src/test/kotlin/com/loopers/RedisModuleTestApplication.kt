package com.loopers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.TestConfiguration

/**
 * modules/redis 테스트용 Spring Boot 애플리케이션
 * 통합 테스트에서 @SpringBootTest가 이 클래스를 찾아 컨텍스트를 구성
 */
@TestConfiguration
@SpringBootApplication
class RedisModuleTestApplication

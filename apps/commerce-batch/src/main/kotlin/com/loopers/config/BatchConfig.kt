package com.loopers.config

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.context.annotation.Configuration

/**
 * Spring Batch 인프라 설정
 *
 * @EnableBatchProcessing의 dataSourceRef 속성으로
 * 공통 모듈의 mySqlMainDataSource를 사용하도록 지정
 */
@Configuration
@EnableBatchProcessing(dataSourceRef = "mySqlMainDataSource")
class BatchConfig

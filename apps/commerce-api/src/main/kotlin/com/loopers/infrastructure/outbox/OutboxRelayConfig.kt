package com.loopers.infrastructure.outbox

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OutboxRelayProperties::class)
class OutboxRelayConfig

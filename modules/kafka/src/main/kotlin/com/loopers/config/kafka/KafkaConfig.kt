package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.util.backoff.FixedBackOff
import java.util.HashMap

@EnableKafka
@Configuration
class KafkaConfig {
    companion object {
        const val BATCH_LISTENER = "BATCH_LISTENER_DEFAULT"

        private const val MAX_POLLING_SIZE = 3000 // read 3000 msg
        private const val FETCH_MIN_BYTES = (1024 * 1024) // 1mb
        private const val FETCH_MAX_WAIT_MS = 5 * 1000 // broker waiting time = 5s
        private const val SESSION_TIMEOUT_MS = 60 * 1000 // session timeout = 1m
        private const val HEARTBEAT_INTERVAL_MS = 20 * 1000 // heartbeat interval = 20s ( 1/3 of session_timeout )
        private const val MAX_POLL_INTERVAL_MS = 2 * 60 * 1000 // max poll interval = 2m
    }

    @Bean
    fun producerFactory(
        kafkaProperties: KafkaProperties,
    ): ProducerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildProducerProperties())
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun consumerFactory(
        kafkaProperties: KafkaProperties,
    ): ConsumerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildConsumerProperties())
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<Any, Any>): KafkaTemplate<Any, Any> = KafkaTemplate(producerFactory)

    @Bean
    fun jsonMessageConverter(
        objectMapper: ObjectMapper,
    ): ByteArrayJsonMessageConverter = ByteArrayJsonMessageConverter(objectMapper)

    @Bean
    fun kafkaListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        kafkaTemplate: KafkaTemplate<Any, Any>,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())

        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        factory.setConsumerFactory(DefaultKafkaConsumerFactory(consumerConfig))
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        factory.setCommonErrorHandler(createErrorHandler(kafkaTemplate))
        return factory
    }

    /**
     * DLQ를 위한 Error Handler 생성
     * 실패한 메시지를 {원본토픽}-dlq로 라우팅
     */
    private fun createErrorHandler(kafkaTemplate: KafkaTemplate<Any, Any>): CommonErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { consumerRecord, _ ->
            org.apache.kafka.common.TopicPartition(
                consumerRecord.topic() + "-dlq",
                consumerRecord.partition(),
            )
        }

        // 3회 재시도 (초기 1회 + 재시도 2회), 각 재시도 간격 1초
        val backOff = FixedBackOff(1000L, 2L)

        return DefaultErrorHandler(recoverer, backOff).apply {
            // 재시도할 수 없는 예외는 즉시 DLQ로 전송 (예: 직렬화 오류, 잘못된 데이터 형식 등)
            addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException::class.java,
                org.springframework.messaging.converter.MessageConversionException::class.java,
            )
        }
    }

    @Bean(BATCH_LISTENER)
    fun defaultBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties()).apply {
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE)
            put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES)
            put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS)
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS)
            put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS)
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS)
        }

        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        factory.setConsumerFactory(DefaultKafkaConsumerFactory(consumerConfig))
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setBatchMessageConverter(BatchMessagingMessageConverter(converter))
        factory.setConcurrency(3)
        factory.setBatchListener(true)
        return factory
    }
}

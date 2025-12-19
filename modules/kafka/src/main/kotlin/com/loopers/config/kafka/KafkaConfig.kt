package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import java.util.HashMap

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaConsumerProperties::class)
class KafkaConfig(
    private val consumerProperties: KafkaConsumerProperties,
) {
    companion object {
        const val BATCH_LISTENER = "BATCH_LISTENER_DEFAULT"
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
    fun kafkaTemplate(
        kafkaProperties: KafkaProperties,
    ): KafkaTemplate<String, String> {
        val props = HashMap(kafkaProperties.buildProducerProperties()).apply {
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        }
        return KafkaTemplate(DefaultKafkaProducerFactory(props))
    }

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): ByteArrayJsonMessageConverter {
        return ByteArrayJsonMessageConverter(objectMapper)
    }

    @Bean(BATCH_LISTENER)
    fun defaultBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
            .apply {
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProperties.maxPollRecords)
                put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, consumerProperties.fetchMinBytes)
                put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, consumerProperties.fetchMaxWaitMs)
                put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, consumerProperties.sessionTimeoutMs)
                put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, consumerProperties.heartbeatIntervalMs)
                put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, consumerProperties.maxPollIntervalMs)
            }

        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setBatchMessageConverter(BatchMessagingMessageConverter(converter))
            setConcurrency(3)
            isBatchListener = true
        }
    }
}

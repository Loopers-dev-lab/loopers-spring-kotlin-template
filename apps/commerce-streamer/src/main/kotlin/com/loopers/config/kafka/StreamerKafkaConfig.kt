package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

/**
 * commerce-streamer용 Kafka 설정
 * 공통 모듈의 KafkaConfig를 확장하여 사용
 */
@Configuration
class StreamerKafkaConfig {

    /**
     * 공통 모듈의 BATCH_LISTENER_DEFAULT를 batchKafkaListenerContainerFactory로 alias
     */
    @Bean
    fun batchKafkaListenerContainerFactory(
        @Qualifier(KafkaConfig.BATCH_LISTENER)
        defaultBatchListener: ConcurrentKafkaListenerContainerFactory<*, *>
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        return defaultBatchListener
    }

    /**
     * Manual Ack 모드 리스너 (단건 처리용)
     * String Deserializer 명시적 설정 (Producer가 StringSerializer 사용)
     */
    @Bean
    fun manualAckKafkaListenerContainerFactory(
        kafkaProperties: KafkaProperties
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()

        val consumerProps = HashMap(kafkaProperties.buildConsumerProperties()).apply {
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            // String Deserializer 명시적 설정
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        }

        factory.consumerFactory = DefaultKafkaConsumerFactory(consumerProps)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL

        return factory
    }
}

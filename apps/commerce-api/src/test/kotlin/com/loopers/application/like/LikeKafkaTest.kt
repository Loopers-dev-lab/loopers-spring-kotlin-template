package com.loopers.application.like

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.IntegrationTestSupport
import com.loopers.domain.common.vo.Money
import com.loopers.domain.outbox.OutboxStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.user.UserFixture
import com.loopers.event.EventType
import com.loopers.event.LikeEventPayload
import com.loopers.infrastructure.outbox.OutBoxJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.signal.ProductTotalSignalJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit

@EmbeddedKafka(
    topics = [EventType.Topic.LIKE_EVENT],
    brokerProperties = ["listeners=PLAINTEXT://localhost:19092", "port=19092"],
)
class LikeKafkaTest(
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val productTotalSignalRepository: ProductTotalSignalJpaRepository,
    private val outBoxJpaRepository: OutBoxJpaRepository,
    private val likeFacade: LikeFacade,
    private val objectMapper: ObjectMapper,
    private val embeddedKafkaBroker: EmbeddedKafkaBroker,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 성공 시 Outbox 저장 및 Kafka 메시지 전송 테스트")
    @Test
    fun likeSuccess_outboxSavedAndKafkaMessageSent() {
        // arrange
        val testUser = UserFixture.create()
        userRepository.save(testUser)

        val testProduct =
            ProductModel.create(
                name = "testProduct",
                price = Money(BigDecimal.valueOf(5000L)),
                refBrandId = 12,
            )
        productRepository.save(testProduct)
        val productId = testProduct.id

        val signal = ProductTotalSignalModel(refProductId = productId)
        productTotalSignalRepository.save(signal)

        // Kafka Consumer 설정 - EmbeddedKafkaBroker 사용
        val consumerProps =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafkaBroker.brokersAsString,
                ConsumerConfig.GROUP_ID_CONFIG to
                        "test-consumer-group-${System.currentTimeMillis()}",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to
                        StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to
                        StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            )

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        val consumer = consumerFactory.createConsumer()

        try {
            consumer.subscribe(listOf(EventType.Topic.LIKE_EVENT))

            // act
            likeFacade.like(testUser.id, productId)

            // assert - Kafka 메시지가 전송되었는지 확인
            var kafkaMessageReceived = false
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val records = consumer.poll(Duration.ofSeconds(2))
                if (records.count() > 0) {
                    val record: ConsumerRecord<String, String> = records.iterator().next()
                    assertThat(record.topic()).isEqualTo(EventType.Topic.LIKE_EVENT)

                    val payload =
                        objectMapper.readValue(record.value(), LikeEventPayload::class.java)
                    assertThat(payload.productId).isEqualTo(productId)
                    assertThat(payload.userId).isEqualTo(testUser.id)
                    assertThat(payload.type).isEqualTo(LikeEventPayload.LikeType.LIKED)
                    kafkaMessageReceived = true
                }
                assertThat(kafkaMessageReceived).isTrue()
            }

            // assert - Outbox 상태가 PUBLISHED로 변경되었는지 확인
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val outboxList = outBoxJpaRepository.findAll()
                assertThat(outboxList).hasSize(1)

                val outbox = outboxList[0]
                assertThat(outbox.status).isEqualTo(OutboxStatus.PUBLISHED)
                assertThat(outbox.publishedAt).isNotNull
            }
        } finally {
            consumer.close()
        }
    }
}

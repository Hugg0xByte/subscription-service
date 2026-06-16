package com.globo.subscription.adapter.outbound.messaging;

import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PubSubInitializerTest {

    @Mock
    private PubSubAdmin pubSubAdmin;

    private PubSubInitializer initializer;

    private static final String PENDING_TOPIC = "pendente-de-pagamento";
    private static final String PENDING_SUBSCRIPTION = "pendente-de-pagamento-sub";
    private static final String PROCESSED_TOPIC = "pagamento-processado";
    private static final String PROCESSED_SUBSCRIPTION = "pagamento-processado-sub";
    private static final String PENDING_DLQ_TOPIC = "pendente-de-pagamento-dlq";
    private static final String PROCESSED_DLQ_TOPIC = "pagamento-processado-dlq";

    @BeforeEach
    void setUp() {
        initializer = new PubSubInitializer(
                pubSubAdmin,
                PENDING_TOPIC,
                PENDING_SUBSCRIPTION,
                PROCESSED_TOPIC,
                PROCESSED_SUBSCRIPTION,
                PENDING_DLQ_TOPIC,
                PROCESSED_DLQ_TOPIC
        );
    }

    @Test
    @DisplayName("Should create topics when they don't exist")
    void shouldCreateTopicsWhenTheyDontExist() {
        // Given - all getTopic calls return null (topics don't exist)
        when(pubSubAdmin.getTopic(anyString())).thenReturn(null);
        when(pubSubAdmin.getSubscription(anyString())).thenReturn(null);

        // When
        initializer.initializeTopicsAndSubscriptions();

        // Then - all 4 topics should be created
        verify(pubSubAdmin).createTopic(PENDING_TOPIC);
        verify(pubSubAdmin).createTopic(PROCESSED_TOPIC);
        verify(pubSubAdmin).createTopic(PENDING_DLQ_TOPIC);
        verify(pubSubAdmin).createTopic(PROCESSED_DLQ_TOPIC);
    }

    @Test
    @DisplayName("Should create subscriptions when they don't exist")
    void shouldCreateSubscriptionsWhenTheyDontExist() {
        // Given - topics exist, subscriptions don't
        when(pubSubAdmin.getTopic(anyString())).thenReturn(null);
        when(pubSubAdmin.getSubscription(anyString())).thenReturn(null);

        // When
        initializer.initializeTopicsAndSubscriptions();

        // Then - subscriptions should be created
        verify(pubSubAdmin).createSubscription(PENDING_SUBSCRIPTION, PENDING_TOPIC);
        verify(pubSubAdmin).createSubscription(PROCESSED_SUBSCRIPTION, PROCESSED_TOPIC);
    }

    @Test
    @DisplayName("Should not create topic when it already exists")
    void shouldNotCreateTopicWhenAlreadyExists() {
        // Given - all topics already exist
        Topic existingTopic = Topic.newBuilder().setName("projects/test/topics/test-topic").build();
        when(pubSubAdmin.getTopic(anyString())).thenReturn(existingTopic);
        when(pubSubAdmin.getSubscription(anyString())).thenReturn(null);

        // When
        initializer.initializeTopicsAndSubscriptions();

        // Then - no createTopic calls should be made
        verify(pubSubAdmin, never()).createTopic(anyString());
    }

    @Test
    @DisplayName("Should not create subscription when it already exists")
    void shouldNotCreateSubscriptionWhenAlreadyExists() {
        // Given - topics don't exist, but subscriptions do
        when(pubSubAdmin.getTopic(anyString())).thenReturn(null);
        Subscription existingSub = Subscription.newBuilder()
                .setName("projects/test/subscriptions/test-sub")
                .build();
        when(pubSubAdmin.getSubscription(anyString())).thenReturn(existingSub);

        // When
        initializer.initializeTopicsAndSubscriptions();

        // Then - no createSubscription calls should be made
        verify(pubSubAdmin, never()).createSubscription(anyString(), anyString());
    }

    @Test
    @DisplayName("Should not propagate exception when PubSubAdmin throws on getTopic")
    void shouldNotPropagateExceptionFromGetTopic() {
        // Given - PubSubAdmin throws on getTopic
        when(pubSubAdmin.getTopic(anyString())).thenThrow(new RuntimeException("Connection refused"));
        when(pubSubAdmin.getSubscription(anyString())).thenThrow(new RuntimeException("Connection refused"));

        // When - should not throw
        initializer.initializeTopicsAndSubscriptions();

        // Then - no topics or subscriptions created (due to exception), but no propagation
        verify(pubSubAdmin, never()).createTopic(anyString());
        verify(pubSubAdmin, never()).createSubscription(anyString(), anyString());
    }

    @Test
    @DisplayName("Should not propagate exception when PubSubAdmin throws on createTopic")
    void shouldNotPropagateExceptionFromCreateTopic() {
        // Given - getTopic returns null but createTopic throws
        when(pubSubAdmin.getTopic(anyString())).thenReturn(null);
        when(pubSubAdmin.createTopic(anyString())).thenThrow(new RuntimeException("Already exists"));
        when(pubSubAdmin.getSubscription(anyString())).thenReturn(null);
        when(pubSubAdmin.createSubscription(anyString(), anyString()))
                .thenThrow(new RuntimeException("Already exists"));

        // When - should not throw
        initializer.initializeTopicsAndSubscriptions();

        // Then - method completes without exception (verified by reaching this point)
    }
}

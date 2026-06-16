package com.globo.subscription.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for User entity — creation and validation.
 */
class UserTest {

    private static final UUID VALID_ID = UUID.randomUUID();
    private static final String VALID_NAME = "Hugo Oliveira";
    private static final String VALID_EMAIL = "hugo@globo.com";
    private static final Instant NOW = Instant.now();

    @Test
    void shouldCreateUserWithValidFields() {
        User user = new User(VALID_ID, VALID_NAME, VALID_EMAIL, true, NOW, NOW);

        assertThat(user.getId()).isEqualTo(VALID_ID);
        assertThat(user.getName()).isEqualTo(VALID_NAME);
        assertThat(user.getEmail()).isEqualTo(VALID_EMAIL);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldCreateInactiveUser() {
        User user = new User(VALID_ID, VALID_NAME, VALID_EMAIL, false, NOW, NOW);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> new User(null, VALID_NAME, VALID_EMAIL, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new User(VALID_ID, null, VALID_EMAIL, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new User(VALID_ID, "   ", VALID_EMAIL, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectEmptyName() {
        assertThatThrownBy(() -> new User(VALID_ID, "", VALID_EMAIL, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldRejectNullEmail() {
        assertThatThrownBy(() -> new User(VALID_ID, VALID_NAME, null, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email must not be blank");
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> new User(VALID_ID, VALID_NAME, "   ", true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email must not be blank");
    }

    @Test
    void shouldRejectEmptyEmail() {
        assertThatThrownBy(() -> new User(VALID_ID, VALID_NAME, "", true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email must not be blank");
    }

    @Test
    void shouldRejectNullCreatedAt() {
        assertThatThrownBy(() -> new User(VALID_ID, VALID_NAME, VALID_EMAIL, true, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt must not be null");
    }

    @Test
    void shouldRejectNullUpdatedAt() {
        assertThatThrownBy(() -> new User(VALID_ID, VALID_NAME, VALID_EMAIL, true, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt must not be null");
    }

    @Test
    void shouldPreserveDifferentCreatedAtAndUpdatedAt() {
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2025-06-15T12:30:00Z");

        User user = new User(VALID_ID, VALID_NAME, VALID_EMAIL, true, createdAt, updatedAt);

        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
    }
}

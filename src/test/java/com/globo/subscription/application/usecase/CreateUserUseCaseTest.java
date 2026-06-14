package com.globo.subscription.application.usecase;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.globo.subscription.application.exception.EmailAlreadyExistsException;
import com.globo.subscription.application.port.UserRepositoryPort;
import com.globo.subscription.domain.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private CreateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUserUseCase(userRepositoryPort);
    }

    @Test
    @DisplayName("Should create user successfully when email does not exist")
    void shouldCreateUserSuccessfully() {
        // Given
        String name = "João Silva";
        String email = "joao@example.com";

        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = useCase.execute(name, email);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepositoryPort).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getName()).isEqualTo(name);
        assertThat(savedUser.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when email already exists")
    void shouldThrowWhenEmailAlreadyExists() {
        // Given
        String name = "João Silva";
        String email = "joao@example.com";

        User existingUser = new User(UUID.randomUUID(), "Existing User", email, true, Instant.now(), Instant.now());
        when(userRepositoryPort.findByEmail(email)).thenReturn(Optional.of(existingUser));

        // When / Then
        assertThatThrownBy(() -> useCase.execute(name, email))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepositoryPort, never()).save(any());
    }
}

package com.globo.subscription.adapter.inbound.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.globo.subscription.adapter.inbound.rest.dto.CreateUserRequest;
import com.globo.subscription.adapter.inbound.rest.dto.UserResponse;
import com.globo.subscription.adapter.inbound.rest.mapper.UserRestMapper;
import com.globo.subscription.application.usecase.CreateUserUseCase;
import com.globo.subscription.domain.entity.User;

import jakarta.validation.Valid;

/**
 * REST controller for user management endpoints.
 * Delegates business logic to the CreateUserUseCase and maps domain entities to response DTOs.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final UserRestMapper userRestMapper;

    public UserController(CreateUserUseCase createUserUseCase, UserRestMapper userRestMapper) {
        this.createUserUseCase = createUserUseCase;
        this.userRestMapper = userRestMapper;
    }

    /**
     * Creates a new user.
     *
     * @param request the user creation request containing name and email
     * @return 201 Created with the user response
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = createUserUseCase.execute(request.name(), request.email());
        UserResponse response = userRestMapper.toResponse(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

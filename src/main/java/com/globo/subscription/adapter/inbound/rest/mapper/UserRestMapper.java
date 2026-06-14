package com.globo.subscription.adapter.inbound.rest.mapper;

import org.mapstruct.Mapper;

import com.globo.subscription.adapter.inbound.rest.dto.UserResponse;
import com.globo.subscription.domain.entity.User;

/**
 * MapStruct mapper that converts a User domain entity to UserResponse DTO.
 * Straightforward field-to-field mapping since the fields align directly.
 */
@Mapper(componentModel = "spring")
public interface UserRestMapper {

    /**
     * Maps a User domain entity to a UserResponse DTO.
     *
     * @param user the domain user entity
     * @return the response DTO with all fields mapped
     */
    UserResponse toResponse(User user);
}

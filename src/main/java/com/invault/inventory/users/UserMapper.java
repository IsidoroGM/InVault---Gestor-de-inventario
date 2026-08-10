package com.invault.inventory.users;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.users.dto.UserResponseDTO;

@Component
public class UserMapper {

    public UserResponseDTO toResponseDTO(User user) {
        if (user == null) {
            return null;
        }

        List<RoleName> roles = user.getRoles().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(Role::getName)
                .sorted(Comparator.comparing(Enum::name))
                .toList();

        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getActive()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roles
        );
    }

    public List<UserResponseDTO> toResponseDTOList(List<User> users) {
        if (users == null) {
            return List.of();
        }

        return users.stream().map(this::toResponseDTO).toList();
    }
}

/*
 * UserMapper never exposes password hashes or complete role entities.
 */

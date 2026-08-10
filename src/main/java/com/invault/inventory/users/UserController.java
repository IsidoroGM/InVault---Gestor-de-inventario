package com.invault.inventory.users;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.common.exception.UnauthorizedException;
import com.invault.inventory.users.dto.PasswordChangeRequestDTO;
import com.invault.inventory.users.dto.PasswordResetRequestDTO;
import com.invault.inventory.users.dto.UserCreateRequestDTO;
import com.invault.inventory.users.dto.UserResponseDTO;
import com.invault.inventory.users.dto.UserUpdateRequestDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponseDTO> findAll() {
        return userService.findAll();
    }

    @GetMapping("/active")
    public List<UserResponseDTO> findAllActive() {
        return userService.findAllActive();
    }

    @GetMapping("/{id}")
    public UserResponseDTO findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@Valid @RequestBody UserCreateRequestDTO requestDTO) {
        return userService.create(requestDTO);
    }

    @PutMapping("/{id}")
    public UserResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequestDTO requestDTO) {
        return userService.update(id, requestDTO);
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, Authentication authentication) {
        userService.deactivate(id, authenticatedUserId(authentication));
    }

    @PatchMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable Long id) {
        userService.activate(id);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(
            @Valid @RequestBody PasswordChangeRequestDTO requestDTO,
            Authentication authentication) {
        userService.changeOwnPassword(authenticatedUserId(authentication), requestDTO);
    }

    @PutMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordResetRequestDTO requestDTO) {
        userService.resetPassword(id, requestDTO);
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Authenticated user is required.");
        }

        Number userId = jwt.getClaim("userId");
        if (userId == null || userId.longValue() <= 0) {
            throw new UnauthorizedException("Authenticated user id is missing.");
        }
        return userId.longValue();
    }
}

/*
 * UserController exposes administration separately from each user's own password flow.
 */

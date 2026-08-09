package com.invault.inventory.auth;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Component
public class UserSessionTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION = new OAuth2Error(
            "invalid_token",
            "The token session is no longer valid.",
            null
    );

    private final UserRepository userRepository;

    public UserSessionTokenValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        Object tokenVersionClaim = jwt.getClaim("tokenVersion");

        if (!(userIdClaim instanceof Number userId)
                || userId.longValue() <= 0
                || !(tokenVersionClaim instanceof Number tokenVersion)
                || tokenVersion.longValue() < 0) {
            return invalidSession();
        }

        try {
            return userRepository.findById(userId.longValue())
                    .filter(user -> isCurrentSession(jwt, user, tokenVersion.longValue()))
                    .map(user -> OAuth2TokenValidatorResult.success())
                    .orElseGet(this::invalidSession);
        } catch (IllegalArgumentException exception) {
            return invalidSession();
        }
    }

    private boolean isCurrentSession(Jwt jwt, User user, long tokenVersion) {
        try {
            return Boolean.TRUE.equals(user.getActive())
                    && user.getUsername().equals(jwt.getSubject())
                    && user.getTokenVersion() == tokenVersion
                    && activeRoleNames(user).equals(jwt.getClaimAsStringList("roles"))
                    && Boolean.valueOf(Boolean.TRUE.equals(user.getMustChangePassword()))
                            .equals(jwt.getClaim("mustChangePassword"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<String> activeRoleNames(User user) {
        return user.getRoles().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(role -> role.getName().name())
                .sorted()
                .toList();
    }

    private OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }
}

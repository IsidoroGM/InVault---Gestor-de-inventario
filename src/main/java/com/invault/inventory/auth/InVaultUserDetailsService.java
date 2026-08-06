package com.invault.inventory.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Service
public class InVaultUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public InVaultUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.getActive()))
                .build();
    }
}

/*
 * InVaultUserDetailsService adapts database users and active roles to Spring
 * Security without exposing the User JPA entity as an authenticated principal.
 */

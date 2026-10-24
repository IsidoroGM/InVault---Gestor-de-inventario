package com.invault.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.invault.inventory.auth.LoginRateLimitProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitConfig {}

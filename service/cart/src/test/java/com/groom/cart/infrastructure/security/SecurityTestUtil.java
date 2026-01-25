package com.groom.cart.infrastructure.security;

import com.groom.common.infrastructure.config.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityTestUtil {

    private SecurityTestUtil() {
    }

    public static void mockUser(UUID userId) {
        CustomUserDetails userDetails = new CustomUserDetails(
            userId,
            "test@groom.com",
            "USER"
        );

        Authentication authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
            );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}

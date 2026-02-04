package com.groom.common.util;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.groom.common.infrastructure.config.security.CustomUserDetails;
import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import com.groom.common.enums.UserRole;

@Component
public class SecurityUtil {

	public static UUID getCurrentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated()) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		CustomUserDetails userDetails = (CustomUserDetails)auth.getPrincipal();
		return userDetails.getUserId();
	}

	public static UserRole getCurrentUserRole() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated()) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}

		CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
		return UserRole.valueOf(userDetails.getRole());
	}

}

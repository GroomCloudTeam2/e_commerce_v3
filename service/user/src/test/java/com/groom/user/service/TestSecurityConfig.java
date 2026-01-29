package com.groom.user.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@TestConfiguration
public class TestSecurityConfig {

	@Bean
	SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())

			// 인증 안 하면 302 리다이렉트 말고 401로 떨어지게
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

			.authorizeHttpRequests(auth -> auth
				// 보통 auth/signup/login은 공개
				.requestMatchers("/api/v1/auth/**").permitAll()

				// internal은 테스트에서 인증 없이 호출하고 있으니 일단 open
				.requestMatchers("/internal/**").permitAll()

				// 관리자: 생성/삭제는 MASTER만
				.requestMatchers(HttpMethod.POST, "/api/v1/admin/managers").hasRole("MASTER")
				.requestMatchers(HttpMethod.DELETE, "/api/v1/admin/managers/**").hasRole("MASTER")

				// 그 외 admin API는 MANAGER or MASTER
				.requestMatchers("/api/v1/admin/**").hasAnyRole("MANAGER", "MASTER")

				// 나머지는 로그인 필요
				.anyRequest().authenticated()
			);

		return http.build();
	}
}

package com.groom.common.infrastructure.config.security;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring()
				.requestMatchers(PathRequest.toStaticResources().atCommonLocations())
				.requestMatchers("/pay.html", "/pay-success.html", "/pay-fail.html");
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/test/**", "/swagger-ui/**", "/v3/api-docs/**","/api/orders/**").permitAll()
						// http
						// .csrf(csrf -> csrf.disable()) // 테스트를 위해 CSRF 비활성화
						// .authorizeHttpRequests(auth -> auth
						// .requestMatchers("/test/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						// // ✅ 테스트 경로 허용!
						// .anyRequest().authenticated())
						// .authorizeHttpRequests(auth -> auth
						// 인증/회원가입
						.requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login").permitAll()

						// 결제 관련 엔드포인트 (ready/success/fail/confirm 등 포함)
						.requestMatchers("/api/v1/payments/**").permitAll()

						// 상품 공개 API (구매자용)
						.requestMatchers("/api/v1/products", "/api/v1/products/{productId}").permitAll()

						// 내부 API (서비스 간 통신용)
						.requestMatchers("/api/v1/internal/**").permitAll()
						.requestMatchers("/actuator/**","/internal/**").permitAll()

						// 카테고리 공개 API
						.requestMatchers("/api/v1/categories", "/api/v1/categories/{categoryId}").permitAll()

						// Swagger
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

						// 루트/파비콘
						.requestMatchers("/", "/favicon.ico", "/error").permitAll()

						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
						.anyRequest().authenticated())
				// 사용자 요청 Role 필터 검사
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

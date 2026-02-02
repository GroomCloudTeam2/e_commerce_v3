package com.groom.user.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groom.user.application.service.AuthServiceV1;
import com.groom.user.presentation.dto.request.user.ReqLoginDtoV1;
import com.groom.user.presentation.dto.request.user.ReqSignupDtoV1;
import com.groom.user.presentation.dto.response.user.ResTokenDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
public class AuthControllerV1 {

	private final AuthServiceV1 authService;

	@Operation(summary = "회원가입", description = "USER 또는 OWNER만 가입 가능")
	@PostMapping("/signup")
	public ResponseEntity<Void> signup(@Valid @RequestBody ReqSignupDtoV1 request) {
		authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@Operation(summary = "로그인 (JWT 발급)")
	@PostMapping("/login")
	public ResponseEntity<ResTokenDtoV1> login(@Valid @RequestBody ReqLoginDtoV1 request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		authService.logout();
		return ResponseEntity.ok().build();
	}
}

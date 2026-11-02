package com.metro.authservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metro.authservice.dto.request.ChangePasswordRequestDto;
import com.metro.authservice.dto.request.ForgotPasswordRequestDto;
import com.metro.authservice.dto.request.LoginRequestDto;
import com.metro.authservice.dto.request.RegisterRequestDto;
import com.metro.authservice.dto.request.ResendVerificationRequestDto; // NEW import
import com.metro.authservice.dto.request.ResetPasswordRequestDto;
import com.metro.authservice.dto.request.UpdateRoleRequestDto;
import com.metro.authservice.dto.response.RegisterResponseDto;
import com.metro.authservice.dto.response.UserResponseDto;
import com.metro.authservice.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	@Autowired
	private AuthService authService;

	@PostMapping("/register")
	public RegisterResponseDto register(@Valid @RequestBody RegisterRequestDto request) {
		RegisterResponseDto dto = authService.register(request);
		return dto;
	}

	@GetMapping("/verify")
	public String verify(@RequestParam String token) {
		return authService.verifyEmail(token);
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequestDto request) {
		String token = authService.login(request);

		Map<String, String> response = new HashMap<String, String>();
		response.put("token", token);

		return ResponseEntity.ok(response);
	}

	// NEW: for users whose verification email was lost or failed
	@PostMapping("/resend-verification")
	public ResponseEntity<Map<String, String>> resendVerification(
			@Valid @RequestBody ResendVerificationRequestDto request) {
		authService.resendVerification(request.getEmail());

		Map<String, String> response = new HashMap<String, String>();
		// same message in every case, so it does not reveal whether the email exists
		response.put("message", "If this email is registered and not yet verified, a new email has been sent.");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
		authService.forgotPassword(request.getEmail());

		Map<String, String> response = new HashMap<String, String>();
		// same message whether or not the email is registered
		response.put("message", "If this email is registered, a password reset link has been sent.");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
		authService.resetPassword(request.getToken(), request.getNewPassword());

		Map<String, String> response = new HashMap<String, String>();
		response.put("message", "Password reset successful. You can now log in.");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/change-password")
	public ResponseEntity<Map<String, String>> changePassword(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			@Valid @RequestBody ChangePasswordRequestDto request) {
		authService.changePassword(authHeader, request.getCurrentPassword(), request.getNewPassword());

		Map<String, String> response = new HashMap<String, String>();
		response.put("message", "Password changed successfully");
		return ResponseEntity.ok(response);
	}

	@GetMapping("/me")
	public UserResponseDto me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
		return authService.getCurrentUser(authHeader);
	}

	@GetMapping("/admin/users")
	public List<UserResponseDto> getAllUsers(
			@RequestHeader(value = "Authorization", required = false) String authHeader) {
		return authService.getAllUsers(authHeader);
	}

	// the user's existing token keeps the old role until it expires
	@PatchMapping("/admin/users/{id}/role")
	public UserResponseDto updateUserRole(
			@PathVariable Long id,
			@Valid @RequestBody UpdateRoleRequestDto request,
			@RequestHeader(value = "Authorization", required = false) String authHeader) {
		return authService.updateUserRole(authHeader, id, request.getRole());
	}
}
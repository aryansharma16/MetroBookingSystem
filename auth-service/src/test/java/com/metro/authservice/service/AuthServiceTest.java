package com.metro.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.metro.authservice.entity.User;
import com.metro.authservice.exception.AdminAccessRequiredException;
import com.metro.authservice.exception.InvalidCredentialsException;
import com.metro.authservice.exception.InvalidResetTokenException;
import com.metro.authservice.exception.InvalidRoleChangeException;
import com.metro.authservice.exception.PasswordChangeNotAllowedException;
import com.metro.authservice.repository.UserRepository;
import com.metro.authservice.util.JwtUtil;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@InjectMocks
	private AuthService authService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private EmailService emailService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(authService, "passwordResetExpiryMinutes", 30L);
	}

	@Test
	void forgotPasswordWithUnknownEmailSendsNothing() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		authService.forgotPassword("nobody@example.com");

		verify(userRepository, never()).save(Mockito.any(User.class));
		verifyNoInteractions(emailService);
	}

	@Test
	void forgotPasswordWithLocalUserStoresTokenAndSendsEmail() {
		User user = localUser(1L, "user@example.com", "encoded-old", "USER");
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		authService.forgotPassword("user@example.com");

		assertNotNull(user.getPasswordResetToken());
		assertNotNull(user.getPasswordResetExpiry());
		assertTrue(user.getPasswordResetExpiry().isAfter(LocalDateTime.now().plusMinutes(29)));
		verify(userRepository).save(user);
		verify(emailService).sendPasswordResetEmail("user@example.com", user.getPasswordResetToken());
	}

	@Test
	void resetPasswordWithExpiredTokenThrows() {
		User user = localUser(1L, "user@example.com", "encoded-old", "USER");
		user.setPasswordResetToken("expired-token");
		user.setPasswordResetExpiry(LocalDateTime.now().minusMinutes(1));
		when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

		try {
			authService.resetPassword("expired-token", "New-pass1");
			fail("Expected InvalidResetTokenException");
		} catch (InvalidResetTokenException e) {
			// expected
		}
		verify(userRepository, never()).save(Mockito.any(User.class));
	}

	@Test
	void resetPasswordWithValidTokenEncodesPasswordAndClearsToken() {
		User user = localUser(1L, "user@example.com", "encoded-old", "USER");
		user.setEmailVerified(false);
		user.setPasswordResetToken("valid-token");
		user.setPasswordResetExpiry(LocalDateTime.now().plusMinutes(10));
		when(userRepository.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("New-pass1")).thenReturn("encoded-new");

		authService.resetPassword("valid-token", "New-pass1");

		assertEquals("encoded-new", user.getPassword());
		assertNull(user.getPasswordResetToken());
		assertNull(user.getPasswordResetExpiry());
		assertTrue(user.isEmailVerified());
		verify(userRepository).save(user);
	}

	@Test
	void changePasswordForGoogleUserThrows() {
		User user = localUser(1L, "google@example.com", null, "USER");
		user.setAuthProvider("GOOGLE");
		mockBearerToken("token", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		try {
			authService.changePassword("Bearer token", "Old-pass1", "New-pass1");
			fail("Expected PasswordChangeNotAllowedException");
		} catch (PasswordChangeNotAllowedException e) {
			// expected
		}
		verify(userRepository, never()).save(Mockito.any(User.class));
	}

	@Test
	void changePasswordWithWrongCurrentPasswordThrows() {
		User user = localUser(1L, "user@example.com", "encoded-old", "USER");
		mockBearerToken("token", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Wrong-pass1", "encoded-old")).thenReturn(false);

		try {
			authService.changePassword("Bearer token", "Wrong-pass1", "New-pass1");
			fail("Expected InvalidCredentialsException");
		} catch (InvalidCredentialsException e) {
			// expected
		}
		verify(userRepository, never()).save(Mockito.any(User.class));
	}

	@Test
	void updateUserRoleOnOwnAccountThrows() {
		User admin = localUser(1L, "admin@example.com", "encoded-old", "ADMIN");
		mockBearerToken("token", 1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

		try {
			authService.updateUserRole("Bearer token", 1L, "USER");
			fail("Expected InvalidRoleChangeException");
		} catch (InvalidRoleChangeException e) {
			// expected
		}
		verify(userRepository, never()).save(Mockito.any(User.class));
	}

	@Test
	void updateUserRoleByNonAdminThrows() {
		User caller = localUser(2L, "user@example.com", "encoded-old", "USER");
		mockBearerToken("token", 2L);
		when(userRepository.findById(2L)).thenReturn(Optional.of(caller));

		try {
			authService.updateUserRole("Bearer token", 1L, "ADMIN");
			fail("Expected AdminAccessRequiredException");
		} catch (AdminAccessRequiredException e) {
			// expected
		}
		verify(userRepository, never()).save(Mockito.any(User.class));
	}

	private User localUser(Long id, String email, String password, String role) {
		User user = new User();
		user.setId(id);
		user.setEmail(email);
		user.setName("Test User");
		user.setPassword(password);
		user.setAuthProvider("LOCAL");
		user.setRole(role);
		user.setEmailVerified(true);
		return user;
	}

	private void mockBearerToken(String token, Long userId) {
		Claims claims = mock(Claims.class);
		when(jwtUtil.isTokenValid(token)).thenReturn(true);
		when(jwtUtil.extractClaims(token)).thenReturn(claims);
		when(claims.get("userId")).thenReturn(userId);
	}
}

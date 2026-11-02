package com.metro.authservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.metro.authservice.dto.request.LoginRequestDto;
import com.metro.authservice.dto.request.RegisterRequestDto;
import com.metro.authservice.dto.response.RegisterResponseDto;
import com.metro.authservice.dto.response.UserResponseDto;
import com.metro.authservice.entity.User;
import com.metro.authservice.exception.AdminAccessRequiredException;
import com.metro.authservice.exception.EmailAlreadyExistsException;
import com.metro.authservice.exception.EmailNotVerifiedException;
import com.metro.authservice.exception.EmailSendFailedException; // NEW import
import com.metro.authservice.exception.InvalidCredentialsException;
import com.metro.authservice.exception.InvalidResetTokenException;
import com.metro.authservice.exception.InvalidRoleChangeException;
import com.metro.authservice.exception.InvalidVerificationTokenException;
import com.metro.authservice.exception.PasswordChangeNotAllowedException;
import com.metro.authservice.exception.UnauthorizedException;
import com.metro.authservice.exception.UserNotFoundException;
import com.metro.authservice.repository.UserRepository;
import com.metro.authservice.util.JwtUtil;

import jakarta.annotation.PostConstruct; // NEW import

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class); // NEW

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailService emailService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtUtil jwtUtil;

	@Value("${app.password-reset.expiry-minutes:30}")
	private long passwordResetExpiryMinutes;

	// NEW: a real BCrypt hash of a throwaway password. Login checks it when the email is unknown,
	// so unknown and known emails take about the same time (closes the timing leak).
	private String dummyHash;

	@PostConstruct
	public void init() {
		dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	// CHANGED: @Transactional, so if the email step fails the user row is rolled back (no stuck user)
	@Transactional
	public RegisterResponseDto register(RegisterRequestDto request) {

		// friendly early exit; the DB unique constraint is the real guarantee (see saveAndFlush below)
		if (userRepository.findByEmail(request.getEmail()).isPresent()) {
			throw new EmailAlreadyExistsException(request.getEmail());
		}

		String hashedPassword = passwordEncoder.encode(request.getPassword());
		String token = UUID.randomUUID().toString();

		User user = new User();
		user.setEmail(request.getEmail());
		user.setName(request.getName());
		user.setPassword(hashedPassword);
		user.setVerificationToken(token);
		user.setAuthProvider("LOCAL");
		user.setRole("USER");

		// CHANGED: saveAndFlush forces the INSERT to run right here, inside the try, so a
		// duplicate from a simultaneous request is caught and answered with the same 409
		User savedUser;
		try {
			savedUser = userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			throw new EmailAlreadyExistsException(request.getEmail());
		}

		// CHANGED: a mail failure now throws, which rolls the user row back
		try {
			emailService.sendVerificationEmail(request.getEmail(), token);
		} catch (MailException e) {
			log.error("Verification email failed for {}: {}", request.getEmail(), e.getMessage());
			throw new EmailSendFailedException();
		}

		RegisterResponseDto response = new RegisterResponseDto();
		response.setId(savedUser.getId());
		response.setEmail(savedUser.getEmail());
		response.setName(savedUser.getName());
		response.setMessage("Registration successful. Please check your email to verify your account.");

		return response;
	}

	public String verifyEmail(String token) {
		// CHANGED: orElseThrow lambda replaced with a plain null check (no-lambda rule)
		User user = userRepository.findByVerificationToken(token).orElse(null);
		if (user == null) {
			throw new InvalidVerificationTokenException();
		}

		user.setEmailVerified(true);
		user.setVerificationToken(null);
		userRepository.save(user);

		return "Email verified successfully. You can now log in.";
	}

	// CHANGED: always runs exactly one BCrypt check, so timing does not reveal which emails exist.
	// It also handles Google users, who have no password (null).
	public String login(LoginRequestDto request) {
		User user = userRepository.findByEmail(request.getEmail()).orElse(null);

		String hashToCheck = dummyHash;
		if (user != null && user.getPassword() != null) {
			hashToCheck = user.getPassword();
		}
		boolean passwordOk = passwordEncoder.matches(request.getPassword(), hashToCheck);

		// unknown email, Google-only account, or wrong password: all the same generic 401
		if (user == null || user.getPassword() == null || !passwordOk) {
			throw new InvalidCredentialsException();
		}

		if (!user.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		return jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
	}

	// NEW: sends a fresh verification email. Does nothing (silently) if the email is unknown,
	// already verified, or a Google account, so the caller can't learn which emails exist.
	@Transactional
	public void resendVerification(String email) {
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null || user.isEmailVerified() || !"LOCAL".equals(user.getAuthProvider())) {
			return;
		}

		String token = UUID.randomUUID().toString();
		user.setVerificationToken(token);
		userRepository.save(user);

		try {
			emailService.sendVerificationEmail(email, token);
		} catch (MailException e) {
			log.error("Resend verification email failed for {}: {}", email, e.getMessage());
			throw new EmailSendFailedException();
		}
	}

	// sends a password reset link. Does nothing (silently) for unknown emails and Google accounts,
	// so the caller can't learn which emails exist.
	@Transactional
	public void forgotPassword(String email) {
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null || !"LOCAL".equals(user.getAuthProvider())) {
			return;
		}

		String token = UUID.randomUUID().toString();
		user.setPasswordResetToken(token);
		user.setPasswordResetExpiry(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes));
		userRepository.save(user);

		try {
			emailService.sendPasswordResetEmail(email, token);
		} catch (MailException e) {
			log.error("Password reset email failed for {}: {}", email, e.getMessage());
			throw new EmailSendFailedException();
		}
	}

	@Transactional
	public void resetPassword(String token, String newPassword) {
		User user = userRepository.findByPasswordResetToken(token).orElse(null);
		if (user == null || user.getPasswordResetExpiry() == null
				|| user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
			throw new InvalidResetTokenException();
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		user.setPasswordResetToken(null);
		user.setPasswordResetExpiry(null);
		// the reset link was sent to this inbox, so the email counts as verified too
		user.setEmailVerified(true);
		userRepository.save(user);
	}

	@Transactional
	public void changePassword(String authHeader, String currentPassword, String newPassword) {
		User user = getUserFromAuthHeader(authHeader);
		if (user.getPassword() == null) {
			throw new PasswordChangeNotAllowedException();
		}
		if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}

	public UserResponseDto getCurrentUser(String authHeader) {
		return toUserResponseDto(getUserFromAuthHeader(authHeader));
	}

	public List<UserResponseDto> getAllUsers(String authHeader) {
		requireAdmin(getUserFromAuthHeader(authHeader));

		List<UserResponseDto> result = new ArrayList<UserResponseDto>();
		for (User user : userRepository.findAll()) {
			result.add(toUserResponseDto(user));
		}
		return result;
	}

	@Transactional
	public UserResponseDto updateUserRole(String authHeader, Long userId, String role) {
		User caller = getUserFromAuthHeader(authHeader);
		requireAdmin(caller);

		if (userId.equals(caller.getId())) {
			throw new InvalidRoleChangeException();
		}

		User target = userRepository.findById(userId).orElse(null);
		if (target == null) {
			throw new UserNotFoundException(userId);
		}

		// the target's current JWT keeps the old role until it expires (about 24 hours)
		target.setRole(role);
		User saved = userRepository.save(target);
		return toUserResponseDto(saved);
	}

	// reads the userId claim from the Bearer token and loads that user from the DB
	public User getUserFromAuthHeader(String authHeader) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new UnauthorizedException();
		}

		String token = authHeader.substring(7);
		if (!jwtUtil.isTokenValid(token)) {
			throw new UnauthorizedException();
		}

		Object userId = jwtUtil.extractClaims(token).get("userId");
		if (!(userId instanceof Number)) {
			throw new UnauthorizedException();
		}

		User user = userRepository.findById(((Number) userId).longValue()).orElse(null);
		if (user == null) {
			throw new UnauthorizedException();
		}
		return user;
	}

	// the role is checked against the DB, not the token
	private void requireAdmin(User user) {
		if (!"ADMIN".equals(user.getRole())) {
			throw new AdminAccessRequiredException();
		}
	}

	private UserResponseDto toUserResponseDto(User user) {
		UserResponseDto dto = new UserResponseDto();
		dto.setId(user.getId());
		dto.setEmail(user.getEmail());
		dto.setName(user.getName());
		dto.setRole(user.getRole());
		dto.setAuthProvider(user.getAuthProvider());
		dto.setEmailVerified(user.isEmailVerified());
		return dto;
	}
}
package com.metro.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.metro.authservice.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);
	
	Optional<User> findByVerificationToken(String verificationToken);
	
	Optional<User> findByGoogleId(String googleId);

	Optional<User> findByPasswordResetToken(String passwordResetToken);
}

package com.metro.authservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.metro.authservice.entity.User;
import com.metro.authservice.repository.UserRepository;

@Component
public class AdminSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	@Value("${app.admin.email:}")
	private String adminEmail;

	@Value("${app.admin.password:}")
	private String adminPassword;

	@Value("${app.admin.name:Admin}")
	private String adminName;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (adminEmail.isBlank() || adminPassword.isBlank()) {
			log.info("Admin seeding skipped: ADMIN_EMAIL or ADMIN_PASSWORD is not set");
			return;
		}

		User existing = userRepository.findByEmail(adminEmail).orElse(null);
		if (existing != null) {
			// never change the role of an existing account here
			if (!"ADMIN".equals(existing.getRole())) {
				log.warn("Admin seeding skipped: {} already exists with role USER", adminEmail);
			}
			return;
		}

		User admin = new User();
		admin.setEmail(adminEmail);
		admin.setName(adminName);
		admin.setPassword(passwordEncoder.encode(adminPassword));
		admin.setEmailVerified(true);
		admin.setAuthProvider("LOCAL");
		admin.setRole("ADMIN");
		userRepository.save(admin);

		log.info("Admin user created: {}", adminEmail);
	}
}

package com.metro.authservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	@Autowired
	private JavaMailSender mailSender;
	
	@Value("${app.gateway.base-url}")
	private String gatewayBaseUrl;

	@Value("${app.frontend.base-url}")
	private String frontendBaseUrl;

	public void sendVerificationEmail(String toEmail , String token) {
		String verificationLink = gatewayBaseUrl + "/auth/verify?token=" + token;

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@metrobooking.com");
		message.setTo(toEmail);
		message.setSubject("Verify your Metro Booking account");
		message.setText("Click the link to verify your email: " + verificationLink);

		mailSender.send(message);
	}

	public void sendPasswordResetEmail(String toEmail, String token) {
		String resetLink = frontendBaseUrl + "/reset-password?token=" + token;

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom("noreply@metrobooking.com");
		message.setTo(toEmail);
		message.setSubject("Reset your Metro Booking password");
		message.setText("Click the link to reset your password: " + resetLink);

		mailSender.send(message);
	}
}

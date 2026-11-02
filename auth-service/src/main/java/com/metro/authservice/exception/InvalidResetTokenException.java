package com.metro.authservice.exception;

public class InvalidResetTokenException extends RuntimeException {
	public InvalidResetTokenException() {
		super("Invalid or expired password reset link");
	}
}

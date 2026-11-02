package com.metro.authservice.exception;

public class PasswordChangeNotAllowedException extends RuntimeException {
	public PasswordChangeNotAllowedException() {
		super("This account uses Google sign-in and has no password");
	}
}

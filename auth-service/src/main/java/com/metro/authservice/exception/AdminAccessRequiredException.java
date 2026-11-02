package com.metro.authservice.exception;

public class AdminAccessRequiredException extends RuntimeException {
	public AdminAccessRequiredException() {
		super("Admin access required");
	}
}

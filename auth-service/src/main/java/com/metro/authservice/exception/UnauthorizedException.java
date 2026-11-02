package com.metro.authservice.exception;

public class UnauthorizedException extends RuntimeException {
	public UnauthorizedException() {
		super("Invalid or missing token");
	}
}

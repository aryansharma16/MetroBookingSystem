package com.metro.authservice.exception;

public class InvalidRoleChangeException extends RuntimeException {
	public InvalidRoleChangeException() {
		super("You cannot change your own role");
	}
}

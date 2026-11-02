package com.metro.authservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(EmailAlreadyExistsException.class)
	public ResponseEntity<Map<String, String>> HandleEmailExists(EmailAlreadyExistsException ex){
		return buildResponse(ex.getMessage(), HttpStatus.CONFLICT); //409 - incoming data was all correct but its already exists
		
	}
	
	@ExceptionHandler(InvalidVerificationTokenException.class)
	public ResponseEntity<Map<String, String>> HandleVerification(InvalidVerificationTokenException ex){
		return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
	    return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
	}
	@ExceptionHandler(EmailNotVerifiedException.class)
	public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException ex) {
	    return buildResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
	}
	// NEW
	@ExceptionHandler(EmailSendFailedException.class)
	public ResponseEntity<Map<String, String>> handleEmailSendFailed(EmailSendFailedException ex) {
	    return buildResponse(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
	}

	@ExceptionHandler(InvalidResetTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidResetToken(InvalidResetTokenException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(PasswordChangeNotAllowedException.class)
	public ResponseEntity<Map<String, String>> handlePasswordChangeNotAllowed(PasswordChangeNotAllowedException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(AdminAccessRequiredException.class)
	public ResponseEntity<Map<String, String>> handleAdminAccessRequired(AdminAccessRequiredException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(InvalidRoleChangeException.class)
	public ResponseEntity<Map<String, String>> handleInvalidRoleChange(InvalidRoleChangeException ex) {
		return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String , String>> HandleValidationErrors(MethodArgumentNotValidException ex){
		Map<String , String> errors = new HashMap<>();
		for(FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.put(error.getField(), error.getDefaultMessage());
		}
		return new ResponseEntity<>(errors,HttpStatus.BAD_REQUEST);
	}
	
	public ResponseEntity<Map<String,String>> buildResponse(String message , HttpStatus status){
		Map<String , String> error=new HashMap<>();
		error.put("error", message);
		error.put("status", status.toString());
		return new ResponseEntity<>(error,status);
	
	}
	

}

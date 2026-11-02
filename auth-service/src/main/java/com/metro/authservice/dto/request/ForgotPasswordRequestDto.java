package com.metro.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequestDto {

	@NotBlank(message = "Email is required")
	@Email(message = "Email format is invalid")
	private String email;
}

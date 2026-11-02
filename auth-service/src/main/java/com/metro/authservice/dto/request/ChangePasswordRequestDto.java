package com.metro.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequestDto {

	@NotBlank(message = "Current password is required")
	private String currentPassword;

	@NotBlank(message = "password is required")
	@Size(min=6 , message = "Password must be at least 6 characters")
	@Pattern(
	        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[_\\-@]).{6,}$",
	        message = "Password must be at least 6 characters and include a letter, a number, and one of _ - @"
	    )
	private String newPassword;
}

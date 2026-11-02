package com.metro.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRoleRequestDto {

	@NotBlank(message = "Role is required")
	@Pattern(regexp = "^(USER|ADMIN)$", message = "Role must be USER or ADMIN")
	private String role;
}

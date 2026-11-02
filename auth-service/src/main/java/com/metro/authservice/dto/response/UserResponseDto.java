package com.metro.authservice.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDto {
	private Long id;
	private String email;
	private String name;
	private String role;
	private String authProvider;
	private boolean emailVerified;
}

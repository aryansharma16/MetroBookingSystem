package com.metro.authservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = true)
    private String verificationToken;

    @Column(nullable = true)
    private String passwordResetToken;

    @Column(nullable = true)
    private LocalDateTime passwordResetExpiry;

    @Column(nullable = false)
    private String authProvider;   // "LOCAL" or "GOOGLE"

    @Column(nullable = true, unique = true)
    private String googleId;

    @Column(nullable = false)
    private String role;   // "USER" or "ADMIN"
}



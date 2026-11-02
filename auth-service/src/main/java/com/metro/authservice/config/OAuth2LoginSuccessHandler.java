package com.metro.authservice.config;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.metro.authservice.entity.User;
import com.metro.authservice.repository.UserRepository;
import com.metro.authservice.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final String oauthRedirectUrl;

    public OAuth2LoginSuccessHandler(UserRepository userRepository, JwtUtil jwtUtil,
                                     @Value("${app.frontend.oauth-redirect-url}") String oauthRedirectUrl) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.oauthRedirectUrl = oauthRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");
        Boolean googleEmailVerified = oAuth2User.getAttribute("email_verified");

        // NEW: only trust the email if Google says it is verified
        if (email == null || !Boolean.TRUE.equals(googleEmailVerified)) {
            response.sendRedirect(oauthRedirectUrl + "?error=google_email_not_verified");
            return;
        }

        Optional<User> existingUser = userRepository.findByGoogleId(googleId);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                user = userByEmail.get();
                user.setGoogleId(googleId);
                // an unverified local account: Google just proved ownership, so verify it
                // and remove the old password so the earlier registrant can't use it
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                    user.setVerificationToken(null);
                    user.setPassword(null);
                }
                user = userRepository.save(user);
            } else {
                user = new User();
                user.setEmail(email);
                user.setName(name);
                user.setGoogleId(googleId);
                user.setEmailVerified(true);
                user.setRole("USER");
                user.setAuthProvider("GOOGLE");
                user = userRepository.save(user);
            }
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        response.sendRedirect(oauthRedirectUrl + "?token=" + token);
    }
}
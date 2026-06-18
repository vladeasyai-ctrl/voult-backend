package com.example.vault.security;

import com.example.vault.config.JwtProperties;
import com.example.vault.exception.ApiException;
import com.example.vault.security.dto.AuthResponse;
import com.example.vault.security.dto.GoogleLoginRequest;
import com.example.vault.security.dto.LoginRequest;
import com.example.vault.security.dto.RegisterRequest;
import com.example.vault.security.entity.AuthProvider;
import com.example.vault.security.entity.User;
import com.example.vault.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VaultUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;
    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already exists");
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(request.username())
                .email(request.email().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build();
        userRepository.save(user);

        return buildAuthResponse(user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        return buildAuthResponse(request.username());
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdTokenVerifierService.GoogleUserInfo googleUser =
                googleIdTokenVerifierService.verify(request.idToken());

        User user = userRepository.findByGoogleSub(googleUser.sub())
                .or(() -> userRepository.findByEmail(googleUser.email()))
                .orElseGet(() -> createGoogleUser(googleUser));

        if (!StringUtils.hasText(user.getGoogleSub())) {
            user.setGoogleSub(googleUser.sub());
            userRepository.save(user);
        }

        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "User account is disabled");
        }

        return buildAuthResponse(user.getUsername());
    }

    private User createGoogleUser(GoogleIdTokenVerifierService.GoogleUserInfo googleUser) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(generateUsername(googleUser))
                .email(googleUser.email())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .googleSub(googleUser.sub())
                .authProvider(AuthProvider.GOOGLE)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    private String generateUsername(GoogleIdTokenVerifierService.GoogleUserInfo googleUser) {
        String base = googleUser.email().split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 90) {
            base = base.substring(0, 90);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private AuthResponse buildAuthResponse(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, "Bearer", jwtProperties.getExpirationMs());
    }
}

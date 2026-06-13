package com.example.vault.security;

import com.example.vault.config.JwtProperties;
import com.example.vault.exception.ApiException;
import com.example.vault.security.dto.AuthResponse;
import com.example.vault.security.dto.LoginRequest;
import com.example.vault.security.dto.RegisterRequest;
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
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
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

    private AuthResponse buildAuthResponse(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, "Bearer", jwtProperties.getExpirationMs());
    }
}

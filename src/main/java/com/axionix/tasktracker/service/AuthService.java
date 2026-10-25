package com.axionix.tasktracker.service;

import com.axionix.tasktracker.dto.AuthResponse;
import com.axionix.tasktracker.dto.LoginRequest;
import com.axionix.tasktracker.dto.RegisterRequest;
import com.axionix.tasktracker.dto.UserResponse;
import com.axionix.tasktracker.entity.User;
import com.axionix.tasktracker.exception.ApiException;
import com.axionix.tasktracker.exception.ErrorCode;
import com.axionix.tasktracker.repository.UserRepository;
import com.axionix.tasktracker.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw duplicateEmail();
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        try {
            // saveAndFlush so a concurrent duplicate is caught here and not at commit time.
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw duplicateEmail();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        // Same error for "unknown email" and "wrong password" so attackers cannot probe which emails exist.
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    /** Emails are compared case-insensitively by always trimming and lower-casing them. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException duplicateEmail() {
        return new ApiException(ErrorCode.DUPLICATE_EMAIL, "An account with this email already exists");
    }
}

package com.yala.service;

import com.yala.dto.auth.ResponseAuthDTO;
import com.yala.dto.auth.RequestLoginDTO;
import com.yala.dto.auth.RequestRefreshTokenDTO;
import com.yala.dto.auth.RequestRegisterDTO;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.EmailAlreadyExistsException;
import com.yala.exceptions.IdentityValidationException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.security.JwtService;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import com.yala.util.TextNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles user registration and login, issuing JWT access and refresh tokens. */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JsonPeService jsonPeService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, JsonPeService jsonPeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jsonPeService = jsonPeService;
    }

    /**
     * Registers a BUYER (role USER). The DNI is validated against RENIEC via JSON.pe and the
     * provided names must match (accent/case-insensitive). On success the user is created as
     * identity-verified. Seller status is obtained later through the seller application + Didit.
     */
    @Transactional
    public ResponseAuthDTO register(RequestRegisterDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(
                    "Email already registered: " + request.email());
        }
        if (userRepository.existsByDni(request.dni())) {
            throw new DuplicateResourceException(
                    "Ya existe una cuenta registrada con ese DNI.");
        }

        validateIdentity(request);

        String fullName = (request.nombres() + " " + request.apellidoPaterno()
                + " " + request.apellidoMaterno()).trim().replaceAll("\\s+", " ");

        User user = User.builder()
                .name(fullName)
                .dni(request.dni())
                .nombres(request.nombres())
                .apellidoPaterno(request.apellidoPaterno())
                .apellidoMaterno(request.apellidoMaterno())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .reputation(0.0f)
                .isVerifiedSeller(false)
                .isIdentityVerified(true)
                .build();
        return buildAuthResponse(userRepository.save(user));
    }

    /**
     * Validates the DNI + names against RENIEC (JSON.pe). When JSON.pe is not configured the
     * backend runs in demo mode and skips the external check (local/dev only).
     */
    private void validateIdentity(RequestRegisterDTO request) {
        if (!jsonPeService.isConfigured()) {
            log.warn("JSON.pe no configurado — registro en modo demo (sin validar DNI) para {}",
                    request.email());
            return;
        }
        var record = jsonPeService.lookup(request.dni())
                .orElseThrow(() -> new IdentityValidationException("No se encontró un DNI válido."));

        // JSON.pe corrompe Ñ/acentos en su origen y los devuelve como '�'; matchesReniec trata ese
        // carácter como comodín para no bloquear a usuarios con apellidos como ZUÑIGA, PEÑA, NUÑEZ.
        boolean matches = TextNormalizer.matchesReniec(record.nombres(), request.nombres())
                && TextNormalizer.matchesReniec(record.apellidoPaterno(), request.apellidoPaterno())
                && TextNormalizer.matchesReniec(record.apellidoMaterno(), request.apellidoMaterno());
        if (!matches) {
            throw new IdentityValidationException(
                    "Los nombres o apellidos no coinciden con el DNI.");
        }
    }

    @Transactional(readOnly = true)
    public ResponseAuthDTO login(RequestLoginDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public ResponseAuthDTO refreshToken(RequestRefreshTokenDTO request) {
        String token = request.refreshToken();
        if (!jwtService.isTokenValid(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid or expired refresh token"));
        String accessToken = jwtService.generateAccessToken(user);
        return ResponseAuthDTO.of(user, accessToken, token);
    }

    private ResponseAuthDTO buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return ResponseAuthDTO.of(user, accessToken, refreshToken);
    }
}

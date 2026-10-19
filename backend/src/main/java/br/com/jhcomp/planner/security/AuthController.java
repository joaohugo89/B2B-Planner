package br.com.jhcomp.planner.security;

import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Emissão e renovação de tokens JWT")
public class AuthController {

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record LogoutResponse(boolean revoked) { }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String name, String role) { }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByEmail(request.email().toLowerCase())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));

        return ResponseEntity.ok(issue(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken())
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido ou expirado."));

        AppUser user = users.findByEmail(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado."));

        // Sair de todas as sessões incrementa a versão: refresh tokens antigos param aqui.
        if (jwtService.tokenVersion(claims) != user.getTokenVersion()) {
            throw new BadCredentialsException("Refresh token revogado.");
        }

        return ResponseEntity.ok(issue(user));
    }

    /**
     * Encerra as sessões do usuário dono do refresh token: a versão sobe e nenhum refresh
     * token emitido antes disso volta a valer. Access tokens já emitidos expiram sozinhos.
     */
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken())
                .filter(jwtService::isRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido ou expirado."));

        AppUser user = users.findByEmail(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado."));

        if (jwtService.tokenVersion(claims) != user.getTokenVersion()) {
            throw new BadCredentialsException("Refresh token revogado.");
        }

        user.revokeTokens();
        users.save(user);
        return ResponseEntity.ok(new LogoutResponse(true));
    }

    private TokenResponse issue(AppUser user) {
        return new TokenResponse(
                jwtService.generateAccessToken(user.getEmail(), user.getRole(), user.getTokenVersion()),
                jwtService.generateRefreshToken(user.getEmail(), user.getRole(), user.getTokenVersion()),
                jwtService.accessTokenSeconds(),
                user.getName(),
                user.getRole());
    }
}

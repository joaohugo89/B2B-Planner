package br.com.jhcomp.planner.security;

import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.parse(header.substring(7))
                    .filter(jwtService::isAccessToken)
                    .filter(this::versaoAtual)
                    .ifPresent(claims -> authenticate(request, claims));
        }

        chain.doFilter(request, response);
    }

    /**
     * "Sair de todas as sessões" sobe a versão do usuário. Sem esta checagem, um access
     * token roubado continuaria valendo até expirar, e a revogação só pegaria o refresh.
     */
    private boolean versaoAtual(Claims claims) {
        return users.findByEmail(claims.getSubject())
                .map(AppUser::getTokenVersion)
                .filter(versao -> versao == jwtService.tokenVersion(claims))
                .isPresent();
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        String role = claims.get("role", String.class);
        var authorities = List.of(new SimpleGrantedAuthority(role == null ? "ROLE_USER" : role));
        var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

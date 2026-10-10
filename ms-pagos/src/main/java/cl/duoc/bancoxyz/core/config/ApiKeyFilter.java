package cl.duoc.bancoxyz.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticacion interna para peticiones originadas desde los BFFs (Web, Movil, Cajero).
 * Permite el acceso seguro interoperable entre microservicios mediante header 'X-Internal-Api-Key'.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${bank.core.internal-api-key:SecretCoreKeyBancoXYZ2026}")
    private String configuredApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-Internal-Api-Key");
        if (apiKey != null && apiKey.equals(configuredApiKey)) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "BFF-INTERNAL-CLIENT",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_BFF"), new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}

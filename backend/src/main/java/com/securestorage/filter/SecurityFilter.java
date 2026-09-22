package com.securestorage.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securestorage.dto.ErrorResponse;
import com.securestorage.entity.AccessAttempt;
import com.securestorage.entity.BlockedEntity;
import com.securestorage.repository.AccessAttemptRepository;
import com.securestorage.repository.BlockedEntityRepository;
import com.securestorage.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AccessAttemptRepository accessAttemptRepository;
    private final BlockedEntityRepository blockedEntityRepository;
    private final ObjectMapper objectMapper;

    @Value("${security.block.threshold:5}")
    private int blockThreshold;

    @Value("${security.block.duration-minutes:30}")
    private int blockDurationMinutes;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        String ip = extractIp(request);

        // Public routes — never require JWT, never auto-block
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/register") || path.equals("/login")
                || path.startsWith("/share/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isBlocked(ip)) {
            writeError(response, HttpStatus.FORBIDDEN,
                    "Your IP has been temporarily blocked due to repeated failed attempts", path);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isTokenValid(token)) {
                    String email = jwtService.extractEmail(token);
                    Long userId = jwtService.extractUserId(token);
                    if (isBlocked(email) || isBlocked(String.valueOf(userId))) {
                        writeError(response, HttpStatus.FORBIDDEN, "Your account has been temporarily blocked", path);
                        return;
                    }
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    request.setAttribute("authenticatedUserId", userId);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    handleFailedAuth(null, ip, path, "Invalid or expired token");
                    writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token", path);
                    return;
                }
            } catch (Exception e) {
                handleFailedAuth(null, ip, path, "Token validation failed");
                writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token", path);
                return;
            }
        } else {
            // Missing token — 401 but do NOT count toward auto-block
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(status.value(), message, path, LocalDateTime.now());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void handleFailedAuth(Long userId, String ip, String path, String reason) {
        logAttempt(userId, ip, path, "FAILED", reason);
        LocalDateTime since = LocalDateTime.now().minusMinutes(blockDurationMinutes);
        if (accessAttemptRepository.countFailedByIpSince(ip, since) >= blockThreshold) {
            blockEntity(ip, "Exceeded failed attempt threshold from IP");
        }
        if (userId != null) {
            if (accessAttemptRepository.countFailedByUserSince(userId, since) >= blockThreshold) {
                blockEntity(String.valueOf(userId), "Exceeded failed attempt threshold for user");
            }
        }
    }

    private void logAttempt(Long userId, String ip, String endpoint, String status, String reason) {
        accessAttemptRepository.save(AccessAttempt.builder()
                .userId(userId).ip(ip).endpoint(endpoint).status(status)
                .reason(reason).timestamp(LocalDateTime.now()).build());
    }

    private void blockEntity(String ipOrUser, String reason) {
        if (blockedEntityRepository.existsActiveBlock(ipOrUser, LocalDateTime.now())) return;
        blockedEntityRepository.save(BlockedEntity.builder()
                .ipOrUser(ipOrUser).reason(reason)
                .blockedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(blockDurationMinutes)).build());
    }

    private boolean isBlocked(String ipOrUser) {
        return blockedEntityRepository.existsActiveBlock(ipOrUser, LocalDateTime.now());
    }

    private String extractIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}

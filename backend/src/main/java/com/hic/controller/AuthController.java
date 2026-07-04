package com.hic.controller;

import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.SignupRequest;
import com.hic.dto.ApiResponse;
import com.hic.model.User;
import com.hic.service.AuthService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    @PreAuthorize("hasAnyRole('HEAD_OFFICE_HR','OFFICE_HR','DEPARTMENT_HR')")
    public ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
        User.UserType callerRole = extractRole();
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request, callerRole));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(@RequestBody RefreshRequest request) {
        String newToken = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(newToken));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Boolean>> verify(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        boolean valid = authService.verifyToken(token);
        return ResponseEntity.ok(ApiResponse.success(valid));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ResponseEntity.ok(ApiResponse.success(authService.getUserFromToken(token)));
    }

    /**
     * Extracts the caller's role from the security context.
     * The @PreAuthorize annotation on signup() guarantees authentication is present in production.
     * If the security context is unexpectedly empty (security misconfiguration), a warning is logged
     * and EMPLOYEE is returned — this causes AuthService to reject the creation since EMPLOYEE
     * cannot create any user roles.
     */
    private User.UserType extractRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("extractRole called without an authenticated security context — security misconfiguration suspected");
            return User.UserType.EMPLOYEE;
        }
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .map(r -> {
                    try {
                        return User.UserType.valueOf(r);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown role authority '{}' in security context, defaulting to EMPLOYEE", r);
                        return User.UserType.EMPLOYEE;
                    }
                })
                .orElseGet(() -> {
                    log.warn("Authenticated user has no granted authorities, defaulting to EMPLOYEE");
                    return User.UserType.EMPLOYEE;
                });
    }

    @Data
    static class RefreshRequest {
        private String refreshToken;
    }
}

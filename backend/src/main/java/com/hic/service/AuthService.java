package com.hic.service;

import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.SignupRequest;
import com.hic.dto.UserDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.UnauthorizedException;
import com.hic.model.User;
import com.hic.repository.TenantRepository;
import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import com.hic.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtUtil jwtUtil;
    private final PasswordUtil passwordUtil;

    /**
     * Role hierarchy: HEAD_OFFICE_HR > OFFICE_HR > DEPARTMENT_HR > EMPLOYEE.
     * Higher ordinal = lower authority.
     */
    private static int roleRank(User.UserType type) {
        return switch (type) {
            case HEAD_OFFICE_HR -> 0;
            case OFFICE_HR -> 1;
            case DEPARTMENT_HR -> 2;
            case EMPLOYEE -> 3;
        };
    }

    /**
     * @param callerRole authenticated caller's role, or {@code null} for anonymous bootstrap
     */
    public LoginResponse signup(SignupRequest request, User.UserType callerRole) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User.UserType targetRole = User.UserType.EMPLOYEE;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                targetRole = User.UserType.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + request.getRole());
            }
        }

        if (callerRole == null) {
            if (userRepository.count() > 0) {
                throw new UnauthorizedException("Signup requires authentication after the first admin is created");
            }
            if (targetRole != User.UserType.HEAD_OFFICE_HR) {
                throw new BadRequestException("First account must use role HEAD_OFFICE_HR");
            }
        } else if (roleRank(callerRole) >= roleRank(targetRole)) {
            throw new BadRequestException(
                    "You cannot create an account with role " + targetRole + " as your own role is " + callerRole);
        }

        var defaultTenant = tenantRepository.findByTenantCode("DEFAULT")
                .orElseThrow(() -> new BadRequestException("Default tenant not configured"));

        User user = new User();
        user.setUsername(request.getEmail());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPasswordHash(passwordUtil.hashPassword(request.getPassword()));
        user.setUserType(targetRole);
        user.setTenantId(defaultTenant.getId());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getUserType(), user.getTenantId(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return new LoginResponse(token, refreshToken, toDTO(user));
    }

    public LoginResponse login(LoginRequest request) {
        String loginId = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail()
                : request.getUsername();

        if (loginId == null || loginId.isBlank()) {
            throw new UnauthorizedException("Email is required");
        }

        User user = userRepository.findByEmail(loginId)
                .or(() -> userRepository.findByUsername(loginId))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordUtil.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getUserType(), user.getTenantId(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return new LoginResponse(token, refreshToken, toDTO(user));
    }

    public boolean verifyToken(String token) {
        return jwtUtil.validateToken(token);
    }

    public String refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return jwtUtil.generateToken(user.getUsername(), user.getUserType(), user.getTenantId(), user.getId());
    }

    public UserDTO getUserFromToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired token");
        }
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return toDTO(user);
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserType(),
                user.getBranchId(),
                user.getDepartmentId(),
                user.getTenantId()
        );
    }
}

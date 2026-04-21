package com.hic.service;

import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.UserDTO;
import com.hic.exception.UnauthorizedException;
import com.hic.model.User;
import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import com.hic.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordUtil passwordUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordUtil.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getUserType(), user.getTenantId(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getUserType(),
                user.getBranchId(),
                user.getDepartmentId(),
                user.getTenantId()
        );

        return new LoginResponse(token, refreshToken, userDTO);
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

        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getUserType(),
                user.getBranchId(),
                user.getDepartmentId(),
                user.getTenantId()
        );
    }
}

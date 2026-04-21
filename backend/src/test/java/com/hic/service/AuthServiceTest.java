package com.hic.service;

import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.UserDTO;
import com.hic.exception.UnauthorizedException;
import com.hic.model.User;
import com.hic.model.User.UserType;
import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import com.hic.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordUtil passwordUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setEmail("admin@hic.az");
        testUser.setPasswordHash("$2a$10$hashedpassword");
        testUser.setUserType(UserType.HEAD_OFFICE_HR);
        testUser.setBranchId(1L);
    }

    @Test
    void login_validCredentials_returnsLoginResponse() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser));
        when(passwordUtil.verifyPassword("admin123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), any(UserType.class), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("admin")).thenReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("admin");
        assertThat(response.getUser().getUserType()).isEqualTo(UserType.HEAD_OFFICE_HR);
    }

    @Test
    void login_unknownUser_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown");
        request.setPassword("password");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser));
        when(passwordUtil.verifyPassword("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void verifyToken_validToken_returnsTrue() {
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);

        assertThat(authService.verifyToken("valid-token")).isTrue();
    }

    @Test
    void verifyToken_invalidToken_returnsFalse() {
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        assertThat(authService.verifyToken("invalid-token")).isFalse();
    }

    @Test
    void refreshToken_validRefreshToken_returnsNewAccessToken() {
        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken(anyString(), any(UserType.class), any(), any())).thenReturn("new-access-token");

        String newToken = authService.refreshToken("valid-refresh-token");

        assertThat(newToken).isEqualTo("new-access-token");
    }

    @Test
    void refreshToken_invalidRefreshToken_throwsUnauthorizedException() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void getUserFromToken_validToken_returnsUserDTO() {
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractUsername("valid-token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser));

        UserDTO userDTO = authService.getUserFromToken("valid-token");

        assertThat(userDTO).isNotNull();
        assertThat(userDTO.getUsername()).isEqualTo("admin");
        assertThat(userDTO.getEmail()).isEqualTo("admin@hic.az");
    }

    @Test
    void getUserFromToken_invalidToken_throwsUnauthorizedException() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.getUserFromToken("bad-token"))
                .isInstanceOf(UnauthorizedException.class);
    }
}

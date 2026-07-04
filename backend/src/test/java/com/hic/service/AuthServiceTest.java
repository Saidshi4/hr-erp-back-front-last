package com.hic.service;

import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.SignupRequest;
import com.hic.dto.UserDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.UnauthorizedException;
import com.hic.model.User;
import com.hic.model.User.UserType;
import com.hic.repository.TenantRepository;
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
    private TenantRepository tenantRepository;

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
        testUser.setUsername("admin@hic.az");
        testUser.setEmail("admin@hic.az");
        testUser.setPasswordHash("$2a$10$hashedpassword");
        testUser.setUserType(UserType.HEAD_OFFICE_HR);
        testUser.setBranchId(1L);
    }

    // ---- Login tests ----

    @Test
    void login_validEmailCredentials_returnsLoginResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@hic.az");
        request.setPassword("admin123");

        when(userRepository.findByEmail("admin@hic.az")).thenReturn(Optional.of(testUser));
        when(passwordUtil.verifyPassword("admin123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), any(UserType.class), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("admin@hic.az");
        assertThat(response.getUser().getUserType()).isEqualTo(UserType.HEAD_OFFICE_HR);
    }

    @Test
    void login_legacyUsernameField_returnsLoginResponse() {
        // Backward-compat: if email is null, fall back to username field
        LoginRequest request = new LoginRequest();
        request.setUsername("admin@hic.az");
        request.setPassword("admin123");

        when(userRepository.findByEmail("admin@hic.az")).thenReturn(Optional.of(testUser));
        when(passwordUtil.verifyPassword("admin123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), any(UserType.class), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("access-token");
    }

    @Test
    void login_unknownEmail_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@hic.az");
        request.setPassword("password");

        when(userRepository.findByEmail("unknown@hic.az")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("unknown@hic.az")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@hic.az");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail("admin@hic.az")).thenReturn(Optional.of(testUser));
        when(passwordUtil.verifyPassword("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    // ---- Signup / RBAC tests ----

    @Test
    void signup_headOfficeHrCreatesOfficeHr_succeeds() {
        com.hic.model.Tenant tenant = new com.hic.model.Tenant();
        tenant.setId(1L);

        SignupRequest request = new SignupRequest();
        request.setEmail("new@hic.az");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPassword("password1");
        request.setRole("OFFICE_HR");

        when(userRepository.existsByEmail("new@hic.az")).thenReturn(false);
        when(tenantRepository.findByTenantCode("DEFAULT")).thenReturn(Optional.of(tenant));
        when(passwordUtil.hashPassword("password1")).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(anyString(), any(), any(), any())).thenReturn("tok");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("ref");

        LoginResponse response = authService.signup(request, UserType.HEAD_OFFICE_HR);

        assertThat(response).isNotNull();
        assertThat(response.getUser().getUserType()).isEqualTo(UserType.OFFICE_HR);
    }

    @Test
    void signup_callerCannotCreateEqualRole_throws() {
        SignupRequest request = new SignupRequest();
        request.setEmail("peer@hic.az");
        request.setFirstName("Peer");
        request.setLastName("User");
        request.setPassword("password1");
        request.setRole("OFFICE_HR");

        when(userRepository.existsByEmail("peer@hic.az")).thenReturn(false);

        // OFFICE_HR trying to create another OFFICE_HR — same rank, must be rejected
        assertThatThrownBy(() -> authService.signup(request, UserType.OFFICE_HR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("OFFICE_HR");
    }

    @Test
    void signup_callerCannotCreateHigherRole_throws() {
        SignupRequest request = new SignupRequest();
        request.setEmail("boss@hic.az");
        request.setFirstName("Boss");
        request.setLastName("User");
        request.setPassword("password1");
        request.setRole("HEAD_OFFICE_HR");

        when(userRepository.existsByEmail("boss@hic.az")).thenReturn(false);

        // DEPARTMENT_HR trying to create HEAD_OFFICE_HR — higher rank, must be rejected
        assertThatThrownBy(() -> authService.signup(request, UserType.DEPARTMENT_HR))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void signup_duplicateEmail_throws() {
        SignupRequest request = new SignupRequest();
        request.setEmail("existing@hic.az");
        request.setFirstName("Ex");
        request.setLastName("Ist");
        request.setPassword("password1");

        when(userRepository.existsByEmail("existing@hic.az")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request, UserType.HEAD_OFFICE_HR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already registered");
    }

    // ---- Token utility tests ----

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
        when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("admin@hic.az");
        when(userRepository.findByUsername("admin@hic.az")).thenReturn(Optional.of(testUser));
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
        when(jwtUtil.extractUsername("valid-token")).thenReturn("admin@hic.az");
        when(userRepository.findByUsername("admin@hic.az")).thenReturn(Optional.of(testUser));

        UserDTO userDTO = authService.getUserFromToken("valid-token");

        assertThat(userDTO).isNotNull();
        assertThat(userDTO.getEmail()).isEqualTo("admin@hic.az");
    }

    @Test
    void getUserFromToken_invalidToken_throwsUnauthorizedException() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.getUserFromToken("bad-token"))
                .isInstanceOf(UnauthorizedException.class);
    }
}

package com.hic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.SignupRequest;
import com.hic.dto.UserDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.UnauthorizedException;
import com.hic.model.User.UserType;
import com.hic.service.AuthService;
import com.hic.util.JwtUtil;
import com.hic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AuthService authService;

    @Test
    void signup_validRequest_returns201WithTokens() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPassword("password1");
        request.setRole("OFFICE_HR");

        UserDTO userDTO = new UserDTO(2L, "newuser", null,
                "New", "User", UserType.OFFICE_HR, null, null, 1L);
        LoginResponse response = new LoginResponse("access-token", "refresh-token", userDTO);

        when(authService.signup(any(SignupRequest.class), nullable(UserType.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(jsonPath("$.user.username").value("newuser"))
                .andExpect(jsonPath("$.user.userType").value("OFFICE_HR"));
    }

    @Test
    void signup_duplicateUsername_returns400() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("existing");
        request.setFirstName("Existing");
        request.setLastName("User");
        request.setPassword("password1");

        when(authService.signup(any(SignupRequest.class), nullable(UserType.class)))
                .thenThrow(new BadRequestException("Bu istifadəçi adı artıq mövcuddur"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_missingUsername_returns400() throws Exception {
        String body = "{\"firstName\":\"A\",\"lastName\":\"B\",\"password\":\"password1\"}";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_shortPassword_returns400() throws Exception {
        String body = "{\"username\":\"user1\",\"firstName\":\"A\",\"lastName\":\"B\",\"password\":\"abc\"}";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_passwordNoDigit_returns400() throws Exception {
        String body = "{\"username\":\"user1\",\"firstName\":\"A\",\"lastName\":\"B\",\"password\":\"abcdefgh\"}";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        UserDTO userDTO = new UserDTO(1L, "admin", "admin@hic.az", null, null, UserType.HEAD_OFFICE_HR, 1L, null, null);
        LoginResponse response = new LoginResponse("access-token", "refresh-token", userDTO);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.userType").value("HEAD_OFFICE_HR"));
    }

    @Test
    void login_missingUsername_returns400() throws Exception {
        String body = "{\"username\": \"\", \"password\": \"password\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        String body = "{\"username\": \"admin\", \"password\": \"\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_validToken_returnsNewAccessToken() throws Exception {
        when(authService.refreshToken("valid-refresh-token")).thenReturn("new-access-token");

        String body = "{\"refreshToken\": \"valid-refresh-token\"}";

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("new-access-token"));
    }

    @Test
    void login_serviceThrowsUnauthorized_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("İstifadəçi adı və ya şifrə yanlışdır"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}

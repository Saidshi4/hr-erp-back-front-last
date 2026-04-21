package com.hic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hic.dto.LoginRequest;
import com.hic.dto.LoginResponse;
import com.hic.dto.UserDTO;
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
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        UserDTO userDTO = new UserDTO(1L, "admin", "admin@hic.az", UserType.HEAD_OFFICE_HR, 1L, null, null);
        LoginResponse response = new LoginResponse("access-token", "refresh-token", userDTO);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.username").value("admin"));
    }

    @Test
    void login_missingUsername_returns400() throws Exception {
        // username is blank - @NotBlank should trigger @Valid
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
                .thenThrow(new UnauthorizedException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}

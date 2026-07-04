package com.hic.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    // Accepts either email or legacy username for backward compatibility
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Legacy field kept for backward compatibility — if email is blank, fall back to username
    private String username;
}

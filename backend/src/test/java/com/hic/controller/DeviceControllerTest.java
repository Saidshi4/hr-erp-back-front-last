package com.hic.controller;

import com.hic.service.IsapiProxyService;
import com.hic.service.DeviceService;
import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(
        controllers = DeviceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class}
)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IsapiProxyService isapiProxyService;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void getAll_withEnabledFilter_returns200() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.GET), eq("/api/devices"), any(HttpServletRequest.class), isNull()))
                .thenReturn(ResponseEntity.ok("[{\"id\":1}]"));

        mockMvc.perform(get("/api/devices").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":1}]"));

        verify(isapiProxyService).forward(eq(HttpMethod.GET), eq("/api/devices"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void patchEnabled_returns200() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.PATCH), eq("/api/devices/1/enabled"), any(HttpServletRequest.class), eq("{\"enabled\":true}")))
                .thenReturn(ResponseEntity.ok("{\"enabled\":true}"));

        mockMvc.perform(patch("/api/devices/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"enabled\":true}"));

        verify(isapiProxyService).forward(eq(HttpMethod.PATCH), eq("/api/devices/1/enabled"), any(HttpServletRequest.class), eq("{\"enabled\":true}"));
    }

    @Test
    void start_returns200() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.POST), eq("/api/devices/1/start"), any(HttpServletRequest.class), isNull()))
                .thenReturn(ResponseEntity.ok("{\"running\":true}"));

        mockMvc.perform(post("/api/devices/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        verify(isapiProxyService).forward(eq(HttpMethod.POST), eq("/api/devices/1/start"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void status_returns200() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.GET), eq("/api/devices/1/status"), any(HttpServletRequest.class), isNull()))
                .thenReturn(ResponseEntity.ok("{\"online\":true}"));

        mockMvc.perform(get("/api/devices/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true));

        verify(isapiProxyService).forward(eq(HttpMethod.GET), eq("/api/devices/1/status"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void sync_returns200() throws Exception {
        when(isapiProxyService.forward(eq(HttpMethod.POST), eq("/api/devices/1/sync"), any(HttpServletRequest.class), isNull()))
                .thenReturn(ResponseEntity.ok("{\"running\":true}"));

        mockMvc.perform(post("/api/devices/1/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        verify(isapiProxyService).forward(eq(HttpMethod.POST), eq("/api/devices/1/sync"), any(HttpServletRequest.class), isNull());
    }
}

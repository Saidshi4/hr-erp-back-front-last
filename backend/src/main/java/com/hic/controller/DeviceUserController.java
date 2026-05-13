package com.hic.controller;

import com.hic.service.IsapiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/devices/{deviceId}/users")
@RequiredArgsConstructor
public class DeviceUserController {

    private final IsapiProxyService isapiProxyService;

    @PostMapping
    public ResponseEntity<String> createUser(@PathVariable Long deviceId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices/" + deviceId + "/users", request, body);
    }

    @GetMapping
    public ResponseEntity<String> getUsers(@PathVariable Long deviceId, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/devices/" + deviceId + "/users", request, null);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<String> getUser(@PathVariable Long deviceId,
                                          @PathVariable Long userId,
                                          HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/devices/" + deviceId + "/users/" + userId, request, null);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.PUT, "/api/devices/" + deviceId + "/users/" + userId, request, body);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.DELETE, "/api/devices/" + deviceId + "/users/" + userId, request, null);
    }

    @PostMapping("/{userId}/sync")
    public ResponseEntity<String> syncUser(@PathVariable Long deviceId,
                                           @PathVariable Long userId,
                                           HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices/" + deviceId + "/users/" + userId + "/sync", request, null);
    }

    @PostMapping(path = "/{userId}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFace(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             MultipartHttpServletRequest request) {
        return isapiProxyService.forwardMultipart("/api/devices/" + deviceId + "/users/" + userId + "/face", request);
    }
}

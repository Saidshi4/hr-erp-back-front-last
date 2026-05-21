package com.hic.controller;

import com.hic.service.DeviceUserIsapiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/devices/{deviceId}/users")
@RequiredArgsConstructor
public class DeviceUserController {

    private final DeviceUserIsapiProxyService deviceUserIsapiProxyService;

    @PostMapping
    public ResponseEntity<String> createUser(@PathVariable Long deviceId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.createUser(deviceId, request, body);
    }

    @GetMapping
    public ResponseEntity<String> getUsers(@PathVariable Long deviceId, HttpServletRequest request) {
        return deviceUserIsapiProxyService.listUsers(deviceId, request);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<String> getUser(@PathVariable Long deviceId,
                                          @PathVariable Long userId,
                                          HttpServletRequest request) {
        return deviceUserIsapiProxyService.getUser(deviceId, userId, request);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             @RequestBody(required = false) String body,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.updateUser(deviceId, userId, request, body);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             HttpServletRequest request) {
        return deviceUserIsapiProxyService.deleteUser(deviceId, userId, request);
    }

    @PostMapping("/{userId}/sync")
    public ResponseEntity<String> syncUser(@PathVariable Long deviceId,
                                            @PathVariable Long userId,
                                            HttpServletRequest request) {
        return deviceUserIsapiProxyService.syncUser(deviceId, userId, request);
    }

    @PostMapping(path = "/{userId}/face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFace(@PathVariable Long deviceId,
                                             @PathVariable Long userId,
                                             MultipartHttpServletRequest request) {
        return deviceUserIsapiProxyService.uploadFace(deviceId, userId, request);
    }
}

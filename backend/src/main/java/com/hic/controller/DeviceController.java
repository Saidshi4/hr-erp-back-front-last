package com.hic.controller;

import com.hic.dto.DeviceSyncDTO;
import com.hic.service.DeviceService;
import com.hic.service.IsapiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;
    private final IsapiProxyService isapiProxyService;

    @GetMapping
    public ResponseEntity<String> getAll(HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/devices", request, null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getById(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/devices/" + id, request, null);
    }

    @PostMapping("/sync")
    public ResponseEntity<DeviceSyncDTO.SyncResultDTO> syncFromIsapi() {
        return ResponseEntity.ok(deviceService.syncFromIsapi());
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody(required = false) String body, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices", request, body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id,
                                         @RequestBody(required = false) String body,
                                         HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.PUT, "/api/devices/" + id, request, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.DELETE, "/api/devices/" + id, request, null);
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<String> updateEnabled(
            @PathVariable Long id,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.PATCH, "/api/devices/" + id + "/enabled", request, body);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<String> start(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices/" + id + "/start", request, null);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<String> stop(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices/" + id + "/stop", request, null);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<String> status(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/devices/" + id + "/status", request, null);
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<String> sync(@PathVariable Long id, HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.POST, "/api/devices/" + id + "/sync", request, null);
    }
}

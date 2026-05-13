package com.hic.controller;

import com.hic.service.IsapiProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventReadController {

    private final IsapiProxyService isapiProxyService;

    @GetMapping("/punches")
    public ResponseEntity<String> getPunches(HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/punches", request, null);
    }

    @GetMapping("/raw-events")
    public ResponseEntity<String> getRawEvents(HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/raw-events", request, null);
    }

    @GetMapping("/failed-attempts")
    public ResponseEntity<String> getFailedAttempts(HttpServletRequest request) {
        return isapiProxyService.forward(HttpMethod.GET, "/api/failed-attempts", request, null);
    }
}

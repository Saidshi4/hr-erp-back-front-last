package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.service.IsapiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Proxies read-only event and attendance-punch queries to the ISAPI microservice.
 *
 * <p>All filtering parameters are optional and forwarded as-is to ISAPI.
 * The {@code deviceId} parameters reference <em>ISAPI</em> device IDs (the IDs
 * returned by ISAPI's {@code GET /api/devices}), not backend device IDs.
 * Use {@code GET /api/devices/{id}/isapi-status} to discover the ISAPI device ID
 * for a specific backend device.
 */
@RestController
@RequestMapping("/api/isapi")
@RequiredArgsConstructor
public class IsapiEventController {

    private final IsapiClientService isapiClientService;

    /**
     * Returns attendance punch records from ISAPI.
     *
     * @param deviceId   optional ISAPI device ID filter
     * @param employeeNo optional employee number filter
     * @param limit      max results (ISAPI default 50, max 500)
     */
    @GetMapping("/punches")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPunches(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) Integer limit) {
        List<Map<String, Object>> punches = isapiClientService.getIsapiPunches(deviceId, employeeNo, limit);
        return ResponseEntity.ok(ApiResponse.success(punches));
    }

    /**
     * Returns raw ACS (Access Control System) event records from ISAPI.
     *
     * @param deviceId       optional ISAPI device ID filter
     * @param major          optional major event type filter
     * @param minor          optional sub-event type filter
     * @param includeRawJson when {@code true}, includes the original JSON payload
     * @param limit          max results (ISAPI default 50, max 500)
     */
    @GetMapping("/raw-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRawEvents(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer major,
            @RequestParam(required = false) Integer minor,
            @RequestParam(defaultValue = "false") boolean includeRawJson,
            @RequestParam(required = false) Integer limit) {
        List<Map<String, Object>> events =
                isapiClientService.getIsapiRawEvents(deviceId, major, minor, includeRawJson, limit);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    /**
     * Returns failed access-attempt records from ISAPI.
     *
     * @param deviceId optional ISAPI device ID filter
     * @param limit    max results (ISAPI default 50, max 500)
     */
    @GetMapping("/failed-attempts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFailedAttempts(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer limit) {
        List<Map<String, Object>> attempts = isapiClientService.getIsapiFailedAttempts(deviceId, limit);
        return ResponseEntity.ok(ApiResponse.success(attempts));
    }
}

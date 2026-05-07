package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DeviceSyncDTO;
import com.hic.dto.IsapiDTO;
import com.hic.service.DeviceSyncService;
import com.hic.service.IsapiClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceSyncService deviceSyncService;
    private final IsapiClientService isapiClientService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceSyncDTO.DeviceConfigDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.getAllDevices()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceSyncDTO.DeviceConfigDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.getDeviceById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceSyncDTO.DeviceConfigDTO>> create(@Valid @RequestBody DeviceSyncDTO.DeviceConfigDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.createDevice(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceSyncDTO.DeviceConfigDTO>> update(@PathVariable Long id, @Valid @RequestBody DeviceSyncDTO.DeviceConfigDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.updateDevice(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deviceSyncService.deleteDevice(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<DeviceSyncDTO.SyncResultDTO>> sync(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.syncDevice(id)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<DeviceSyncDTO.SyncHistoryDTO>>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(deviceSyncService.getSyncHistory(id)));
    }

    // -----------------------------------------------------------------------
    // ISAPI device lifecycle proxy endpoints
    // -----------------------------------------------------------------------

    /**
     * Starts the alert-stream worker on the ISAPI side for the given backend device.
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startDevice(@PathVariable Long id) {
        Long isapiId = resolveIsapiId(id);
        Map<String, Object> result = isapiClientService.startIsapiDevice(isapiId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Stops the alert-stream worker on the ISAPI side for the given backend device.
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopDevice(@PathVariable Long id) {
        Long isapiId = resolveIsapiId(id);
        Map<String, Object> result = isapiClientService.stopIsapiDevice(isapiId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Returns the real-time online status of the device as reported by ISAPI.
     */
    @GetMapping("/{id}/isapi-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIsapiStatus(@PathVariable Long id) {
        Long isapiId = resolveIsapiId(id);
        Map<String, Object> result = isapiClientService.getIsapiDeviceStatus(isapiId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Patches the {@code enabled} flag on the ISAPI device.
     * When {@code enabled=false} ISAPI will stop streaming; {@code true} starts it.
     */
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Map<String, Object>>> patchEnabled(
            @PathVariable Long id,
            @RequestBody IsapiDTO.DeviceEnabledRequest request) {
        Long isapiId = resolveIsapiId(id);
        Map<String, Object> result = isapiClientService.patchIsapiDeviceEnabled(isapiId, request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Resets ISAPI event cursor ({@code lastSerialNo + lastEventTime}) so
     * the history poller no longer applies serial guard filtering after reset.
     */
    @PostMapping("/{id}/isapi-cursor/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetIsapiCursor(@PathVariable Long id) {
        Long isapiId = resolveIsapiId(id);
        Map<String, Object> result = isapiClientService.resetIsapiDeviceCursor(isapiId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves the ISAPI device ID that corresponds to the given backend device ID.
     * Throws 404 if the backend device doesn't exist and 422 if not yet registered in ISAPI.
     */
    private Long resolveIsapiId(Long backendDeviceId) {
        String ip = deviceSyncService.getDeviceIp(backendDeviceId);
        Long isapiId = isapiClientService.findIsapiDeviceIdByIp(ip);
        if (isapiId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Device is not registered in ISAPI yet. Please run sync first.");
        }
        return isapiId;
    }
}

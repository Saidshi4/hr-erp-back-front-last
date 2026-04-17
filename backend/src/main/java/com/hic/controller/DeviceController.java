package com.hic.controller;

import com.hic.dto.DeviceSyncDTO;
import com.hic.dto.ApiResponse;
import com.hic.service.DeviceSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceSyncService deviceSyncService;

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
}

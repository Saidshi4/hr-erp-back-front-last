package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.repository.DeviceConfigRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final DeviceSyncService deviceSyncService;

    public List<DeviceSyncDTO.DeviceConfigDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<DeviceConfig> devices = tenantId != null
                ? deviceConfigRepository.findByTenantId(tenantId)
                : deviceConfigRepository.findAll();
        return devices.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public DeviceSyncDTO.DeviceConfigDTO getById(Long id) {
        Long tenantId = TenantContext.getTenantId();
        DeviceConfig device = deviceConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", id));
        if (tenantId != null && device.getTenantId() != null && !tenantId.equals(device.getTenantId())) {
            throw new BadRequestException("Device does not belong to your tenant");
        }
        return toDTO(device);
    }

    @Transactional
    public DeviceSyncDTO.SyncResultDTO syncFromIsapi() {
        log.info("Syncing devices from ISAPI to database");
        Long tenantId = TenantContext.getTenantId();
        
        try {
            List<DeviceSyncDTO.DeviceConfigDTO> isapiDevices = deviceSyncService.getAllDevices(null);
            int syncedCount = 0;
            
            for (DeviceSyncDTO.DeviceConfigDTO isapiDevice : isapiDevices) {
                DeviceConfig existing = deviceConfigRepository.findByDeviceId(isapiDevice.getDeviceId()).orElse(null);
                
                if (existing == null) {
                    DeviceConfig newDevice = new DeviceConfig();
                    newDevice.setDeviceId(isapiDevice.getDeviceId());
                    newDevice.setDeviceName(isapiDevice.getDeviceName());
                    newDevice.setDeviceIp(isapiDevice.getDeviceIp());
                    newDevice.setUsername(isapiDevice.getUsername());
                    newDevice.setStatus(isapiDevice.getStatus());
                    newDevice.setTenantId(tenantId);
                    deviceConfigRepository.save(newDevice);
                    syncedCount++;
                    log.info("Created new device: {} ({})", isapiDevice.getDeviceName(), isapiDevice.getDeviceId());
                } else {
                    existing.setDeviceName(isapiDevice.getDeviceName());
                    existing.setDeviceIp(isapiDevice.getDeviceIp());
                    existing.setUsername(isapiDevice.getUsername());
                    existing.setStatus(isapiDevice.getStatus());
                    existing.setLastSyncTime(isapiDevice.getLastSyncTime());
                    if (tenantId != null && existing.getTenantId() == null) {
                        existing.setTenantId(tenantId);
                    }
                    deviceConfigRepository.save(existing);
                    syncedCount++;
                    log.info("Updated device: {} ({})", isapiDevice.getDeviceName(), isapiDevice.getDeviceId());
                }
            }
            
            log.info("Device sync completed: {} devices synced", syncedCount);
            return new DeviceSyncDTO.SyncResultDTO(true, "Synced " + syncedCount + " devices from ISAPI", syncedCount);
        } catch (Exception e) {
            log.error("Failed to sync devices from ISAPI", e);
            return new DeviceSyncDTO.SyncResultDTO(false, "Sync failed: " + e.getMessage(), 0);
        }
    }

    private DeviceSyncDTO.DeviceConfigDTO toDTO(DeviceConfig device) {
        DeviceSyncDTO.DeviceConfigDTO dto = new DeviceSyncDTO.DeviceConfigDTO();
        dto.setId(device.getId());
        dto.setDeviceId(device.getDeviceId());
        dto.setDeviceName(device.getDeviceName());
        dto.setDeviceIp(device.getDeviceIp());
        dto.setDevicePort(device.getDevicePort());
        dto.setUsername(device.getUsername());
        dto.setPassword(null);
        dto.setBranchId(device.getBranchId());
        dto.setStatus(device.getStatus());
        dto.setLastSyncTime(device.getLastSyncTime());
        return dto;
    }
}

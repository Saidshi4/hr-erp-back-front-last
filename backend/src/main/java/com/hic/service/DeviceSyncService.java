package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import com.hic.exception.DeviceSyncException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.model.DeviceSyncHistory;
import com.hic.model.DeviceSyncHistory.SyncStatus;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DeviceSyncHistoryRepository;
import com.hic.util.HikvisionUtil;
import com.hic.util.EncryptionUtil;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final DeviceSyncHistoryRepository syncHistoryRepository;
    private final HikvisionUtil hikvisionUtil;
    private final EncryptionUtil encryptionUtil;

    public List<DeviceSyncDTO.DeviceConfigDTO> getAllDevices() {
        Long tenantId = TenantContext.getTenantId();
        List<DeviceConfig> devices = tenantId != null
                ? deviceConfigRepository.findByTenantId(tenantId)
                : deviceConfigRepository.findAll();
        return devices.stream().map(this::toConfigDTO).collect(Collectors.toList());
    }

    public DeviceSyncDTO.DeviceConfigDTO getDeviceById(Long id) {
        return toConfigDTO(deviceConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", id)));
    }

    @Transactional
    public DeviceSyncDTO.DeviceConfigDTO createDevice(DeviceSyncDTO.DeviceConfigDTO dto) {
        DeviceConfig device = new DeviceConfig();
        mapDtoToDevice(dto, device);
        device.setStatus("ACTIVE");
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            device.setTenantId(tenantId);
        }
        return toConfigDTO(deviceConfigRepository.save(device));
    }

    @Transactional
    public DeviceSyncDTO.DeviceConfigDTO updateDevice(Long id, DeviceSyncDTO.DeviceConfigDTO dto) {
        DeviceConfig device = deviceConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", id));
        mapDtoToDevice(dto, device);
        return toConfigDTO(deviceConfigRepository.save(device));
    }

    @Transactional
    public void deleteDevice(Long id) {
        if (!deviceConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("DeviceConfig", id);
        }
        deviceConfigRepository.deleteById(id);
    }

    @Transactional
    public DeviceSyncDTO.SyncResultDTO syncDevice(Long id) {
        DeviceConfig device = deviceConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", id));

        DeviceSyncHistory history = new DeviceSyncHistory();
        history.setDeviceId(device.getDeviceId());
        history.setSyncStartTime(LocalDateTime.now());

        try {
            boolean reachable = hikvisionUtil.testConnection(
                    device.getDeviceIp(),
                    device.getDevicePort() != null ? device.getDevicePort() : 80,
                    device.getUsername(),
                    encryptionUtil.isEncrypted(device.getPasswordEncrypted())
                            ? encryptionUtil.decrypt(device.getPasswordEncrypted())
                            : device.getPasswordEncrypted()
            );

            if (!reachable) {
                throw new DeviceSyncException("Device not reachable: " + device.getDeviceIp());
            }

            history.setSyncEndTime(LocalDateTime.now());
            history.setRecordsSynced(0);
            history.setSyncStatus(SyncStatus.SUCCESS);
            syncHistoryRepository.save(history);

            device.setLastSyncTime(LocalDateTime.now());
            deviceConfigRepository.save(device);

            return new DeviceSyncDTO.SyncResultDTO(true, "Sync completed successfully", 0);
        } catch (Exception e) {
            history.setSyncEndTime(LocalDateTime.now());
            history.setSyncStatus(SyncStatus.FAILED);
            history.setErrorMessage(e.getMessage());
            syncHistoryRepository.save(history);
            log.error("Sync failed for device {}: {}", device.getDeviceId(), e.getMessage());
            return new DeviceSyncDTO.SyncResultDTO(false, e.getMessage(), 0);
        }
    }

    public List<DeviceSyncDTO.SyncHistoryDTO> getSyncHistory(Long id) {
        DeviceConfig device = deviceConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeviceConfig", id));
        return syncHistoryRepository.findByDeviceIdOrderBySyncStartTimeDesc(device.getDeviceId())
                .stream().map(this::toHistoryDTO).collect(Collectors.toList());
    }

    private void mapDtoToDevice(DeviceSyncDTO.DeviceConfigDTO dto, DeviceConfig device) {
        device.setDeviceId(dto.getDeviceId());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceIp(dto.getDeviceIp());
        device.setDevicePort(dto.getDevicePort());
        device.setUsername(dto.getUsername());
        // Encrypt password only if a non-empty value is provided
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            device.setPasswordEncrypted(encryptionUtil.encrypt(dto.getPassword()));
        }
        device.setBranchId(dto.getBranchId());
        if (dto.getStatus() != null) device.setStatus(dto.getStatus());
    }

    private DeviceSyncDTO.DeviceConfigDTO toConfigDTO(DeviceConfig device) {
        DeviceSyncDTO.DeviceConfigDTO dto = new DeviceSyncDTO.DeviceConfigDTO();
        dto.setId(device.getId());
        dto.setDeviceId(device.getDeviceId());
        dto.setDeviceName(device.getDeviceName());
        dto.setDeviceIp(device.getDeviceIp());
        dto.setDevicePort(device.getDevicePort());
        dto.setUsername(device.getUsername());
        dto.setBranchId(device.getBranchId());
        dto.setStatus(device.getStatus());
        dto.setLastSyncTime(device.getLastSyncTime());
        return dto;
    }

    private DeviceSyncDTO.SyncHistoryDTO toHistoryDTO(DeviceSyncHistory h) {
        DeviceSyncDTO.SyncHistoryDTO dto = new DeviceSyncDTO.SyncHistoryDTO();
        dto.setId(h.getId());
        dto.setDeviceId(h.getDeviceId());
        dto.setSyncStartTime(h.getSyncStartTime());
        dto.setSyncEndTime(h.getSyncEndTime());
        dto.setRecordsSynced(h.getRecordsSynced());
        dto.setSyncStatus(h.getSyncStatus() != null ? h.getSyncStatus().name() : null);
        dto.setErrorMessage(h.getErrorMessage());
        return dto;
    }
}

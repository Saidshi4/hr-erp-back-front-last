package com.hic.service;

import com.hic.dto.DeviceSyncDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.DeviceConfig;
import com.hic.model.DeviceSyncHistory;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.DeviceSyncHistoryRepository;
import com.hic.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceSyncServiceTest {

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Mock
    private DeviceSyncHistoryRepository syncHistoryRepository;

    @Mock
    private IsapiClientService isapiClientService;

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private DeviceSyncService deviceSyncService;

    private DeviceConfig device;

    @BeforeEach
    void setUp() {
        device = new DeviceConfig();
        device.setId(1L);
        device.setDeviceId("DEV-001");
        device.setDeviceName("Test Device");
        device.setDeviceIp("192.168.1.100");
        device.setDevicePort(80);
        device.setUsername("admin");
        device.setPasswordEncrypted("plain-password");
        device.setStatus("ACTIVE");
    }

    // -----------------------------------------------------------------------
    // syncDevice – happy path
    // -----------------------------------------------------------------------

    @Test
    void syncDevice_deviceReachable_returnsSuccess() {
        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(device));
        when(encryptionUtil.isEncrypted("plain-password")).thenReturn(false);
        when(isapiClientService.checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "plain-password"))
                .thenReturn(true);
        when(syncHistoryRepository.save(any(DeviceSyncHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(deviceConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceSyncDTO.SyncResultDTO result = deviceSyncService.syncDevice(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successfully");
        verify(isapiClientService).checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "plain-password");
    }

    // -----------------------------------------------------------------------
    // syncDevice – device not reachable
    // -----------------------------------------------------------------------

    @Test
    void syncDevice_deviceNotReachable_returnsFailure() {
        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(device));
        when(encryptionUtil.isEncrypted("plain-password")).thenReturn(false);
        when(isapiClientService.checkDeviceConnectivity(any(), anyInt(), any(), any()))
                .thenReturn(false);
        when(syncHistoryRepository.save(any(DeviceSyncHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceSyncDTO.SyncResultDTO result = deviceSyncService.syncDevice(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("not reachable");
        verify(deviceConfigRepository, never()).save(device);
    }

    // -----------------------------------------------------------------------
    // syncDevice – encrypted password is decrypted before ISAPI call
    // -----------------------------------------------------------------------

    @Test
    void syncDevice_encryptedPassword_decryptsBeforeIsapiCall() {
        device.setPasswordEncrypted("ENC(secret)");

        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(device));
        when(encryptionUtil.isEncrypted("ENC(secret)")).thenReturn(true);
        when(encryptionUtil.decrypt("ENC(secret)")).thenReturn("secret");
        when(isapiClientService.checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "secret"))
                .thenReturn(true);
        when(syncHistoryRepository.save(any(DeviceSyncHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(deviceConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceSyncDTO.SyncResultDTO result = deviceSyncService.syncDevice(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(encryptionUtil).decrypt("ENC(secret)");
        verify(isapiClientService).checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "secret");
    }

    // -----------------------------------------------------------------------
    // syncDevice – default port when port is null
    // -----------------------------------------------------------------------

    @Test
    void syncDevice_nullPort_usesDefaultPort80() {
        device.setDevicePort(null);

        when(deviceConfigRepository.findById(1L)).thenReturn(Optional.of(device));
        when(encryptionUtil.isEncrypted(any())).thenReturn(false);
        when(isapiClientService.checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "plain-password"))
                .thenReturn(true);
        when(syncHistoryRepository.save(any(DeviceSyncHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(deviceConfigRepository.save(any(DeviceConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceSyncDTO.SyncResultDTO result = deviceSyncService.syncDevice(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(isapiClientService).checkDeviceConnectivity(
                "192.168.1.100", 80, "admin", "plain-password");
    }

    // -----------------------------------------------------------------------
    // syncDevice – device not found
    // -----------------------------------------------------------------------

    @Test
    void syncDevice_deviceNotFound_throwsResourceNotFoundException() {
        when(deviceConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceSyncService.syncDevice(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(isapiClientService);
    }
}

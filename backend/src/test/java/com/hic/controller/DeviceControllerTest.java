package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.service.DeviceSyncService;
import com.hic.service.IsapiClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceSyncService deviceSyncService;

    @Mock
    private IsapiClientService isapiClientService;

    @InjectMocks
    private DeviceController deviceController;

    @Test
    void resetIsapiCursor_resolvesIsapiIdAndReturnsResponse() {
        when(deviceSyncService.getDeviceIp(10L)).thenReturn("192.168.0.200");
        when(isapiClientService.findIsapiDeviceIdByIp("192.168.0.200")).thenReturn(1L);
        Map<String, Object> resetResult = new HashMap<>();
        resetResult.put("deviceId", 1L);
        resetResult.put("lastSerialNo", 0L);
        resetResult.put("lastEventTime", null);
        when(isapiClientService.resetIsapiDeviceCursor(1L))
                .thenReturn(resetResult);

        ApiResponse<Map<String, Object>> body = deviceController.resetIsapiCursor(10L).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).containsEntry("deviceId", 1L);
        assertThat(body.getData()).containsEntry("lastSerialNo", 0L);
        verify(isapiClientService).resetIsapiDeviceCursor(1L);
    }

    @Test
    void resetIsapiCursor_whenIsapiResetFails_returnsBadGateway() {
        when(deviceSyncService.getDeviceIp(10L)).thenReturn("192.168.0.200");
        when(isapiClientService.findIsapiDeviceIdByIp("192.168.0.200")).thenReturn(1L);
        when(isapiClientService.resetIsapiDeviceCursor(1L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deviceController.resetIsapiCursor(10L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}

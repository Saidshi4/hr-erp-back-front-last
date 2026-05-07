package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.DeviceCursorService;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCursorService deviceCursorService;
    @Mock
    private DeviceWorkerService deviceWorkerService;
    @Mock
    private IsapiClient isapiClient;

    @InjectMocks
    private DeviceController controller;

    @Test
    void resetCursor_resetsLastSerialNoAndLastEventTime() {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);

        DeviceCursorEntity existingCursor = new DeviceCursorEntity();
        existingCursor.setDeviceId(1L);
        existingCursor.setLastSerialNo(0L);
        existingCursor.setLastEventTime(null);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCursorService.resetCursor(1L)).thenReturn(existingCursor);

        DeviceController.CursorResetResponse response = controller.resetCursor(1L);

        verify(deviceCursorService).resetCursor(1L);
        assertThat(response.deviceId()).isEqualTo(1L);
        assertThat(response.lastSerialNo()).isEqualTo(0L);
        assertThat(response.lastEventTime()).isNull();
    }
}

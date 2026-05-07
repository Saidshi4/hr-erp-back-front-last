package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCursorRepository deviceCursorRepository;
    @Mock
    private DeviceWorkerService deviceWorkerService;
    @Mock
    private IsapiClient isapiClient;

    @InjectMocks
    private DeviceController controller;

    @Test
    void resetCursor_resetsSerialGuardFields() {
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);

        DeviceCursorEntity existingCursor = new DeviceCursorEntity();
        existingCursor.setDeviceId(1L);
        existingCursor.setLastSerialNo(123L);
        existingCursor.setLastEventTime(OffsetDateTime.now().minusMinutes(10));

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCursorRepository.findById(1L)).thenReturn(Optional.of(existingCursor));
        when(deviceCursorRepository.save(any(DeviceCursorEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceController.CursorResetResponse response = controller.resetCursor(1L);

        ArgumentCaptor<DeviceCursorEntity> savedCaptor = ArgumentCaptor.forClass(DeviceCursorEntity.class);
        verify(deviceCursorRepository).save(savedCaptor.capture());
        DeviceCursorEntity saved = savedCaptor.getValue();

        assertThat(saved.getDeviceId()).isEqualTo(1L);
        assertThat(saved.getLastSerialNo()).isEqualTo(0L);
        assertThat(saved.getLastEventTime()).isNull();
        assertThat(response.deviceId()).isEqualTo(1L);
        assertThat(response.lastSerialNo()).isEqualTo(0L);
        assertThat(response.lastEventTime()).isNull();
    }
}

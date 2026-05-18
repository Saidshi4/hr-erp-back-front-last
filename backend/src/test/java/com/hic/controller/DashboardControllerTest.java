package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DeviceSyncDTO;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.service.AttendanceLogSyncService;
import com.hic.service.DeviceSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DeviceConfigRepository deviceConfigRepository;
    @Mock
    private AttendanceLogRepository attendanceLogRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private AttendanceLogSyncService attendanceLogSyncService;
    @Mock
    private DeviceSyncService deviceSyncService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void getDeviceStatus_usesIsapiDeviceStatuses() {
        when(deviceSyncService.getAllDevices(null)).thenReturn(List.of(
                new DeviceSyncDTO.DeviceConfigDTO(1L, "1", "A", "10.0.0.1", 80, "admin", null, null, "ACTIVE", null),
                new DeviceSyncDTO.DeviceConfigDTO(2L, "2", "B", "10.0.0.2", 80, "admin", null, null, "INACTIVE", null),
                new DeviceSyncDTO.DeviceConfigDTO(3L, "3", "C", "10.0.0.3", 80, "admin", null, null, "ACTIVE", null)
        ));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getDeviceStatus();

        Map<String, Object> data = response.getBody().getData();
        assertThat(data.get("totalDevices")).isEqualTo(3L);
        assertThat(data.get("onlineDevices")).isEqualTo(2L);
        assertThat(data.get("offlineDevices")).isEqualTo(1L);
    }

    @Test
    void getLatestAccessLogs_mapsIsapiAttendanceEntries() {
        OffsetDateTime now = OffsetDateTime.now();
        when(attendanceLogSyncService.getAttendanceLogs(null, null, 10)).thenReturn(List.of(
                new AttendanceLogSyncDTO.AttendanceLogEntryDTO(11L, 5L, "1001", now, 88L)
        ));

        ResponseEntity<ApiResponse<List<DashboardController.DashboardAccessLogDTO>>> response =
                dashboardController.getLatestAccessLogs();

        List<DashboardController.DashboardAccessLogDTO> logs = response.getBody().getData();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).id()).isEqualTo(11L);
        assertThat(logs.get(0).employeeNo()).isEqualTo("1001");
        assertThat(logs.get(0).punchTime()).isEqualTo(now);
        assertThat(logs.get(0).deviceId()).isEqualTo(5L);
        assertThat(logs.get(0).rawEventId()).isEqualTo(88L);
        assertThat(logs.get(0).status()).isEqualTo("RECORDED");
    }
}

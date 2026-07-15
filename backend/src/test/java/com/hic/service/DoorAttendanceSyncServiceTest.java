package com.hic.service;

import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoorAttendanceSyncServiceTest {

    @Mock private AttendanceLogSyncService attendanceLogSyncService;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DeviceConfigRepository deviceConfigRepository;
    @Mock private AttendanceCalculationService attendanceCalculationService;
    @Mock private AttendanceService attendanceService;

    @InjectMocks
    private DoorAttendanceSyncService doorAttendanceSyncService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncAllDevices_noActiveEntryExitDevices_returnsEmptyResult() {
        when(deviceConfigRepository.findByTenantId(1L)).thenReturn(List.of());

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncAllDevices(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 2, 0, 0),
                100);

        assertThat(result.getTotalPunches()).isZero();
        assertThat(result.getMatchedSessions()).isZero();
        assertThat(result.getCreatedLogs()).isZero();
        assertThat(result.getSkippedEmployees()).isZero();
        assertThat(result.getUnresolvedEmployeeNos()).isEmpty();
    }
}

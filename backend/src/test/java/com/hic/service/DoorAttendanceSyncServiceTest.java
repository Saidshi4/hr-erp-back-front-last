package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.dto.DoorAttendanceSyncResultDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoorAttendanceSyncServiceTest {

    @Mock
    private AttendanceLogSyncService attendanceLogSyncService;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AttendanceCalculationService attendanceCalculationService;

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private DoorAttendanceSyncService doorAttendanceSyncService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncDoorAttendance_paginatesAndRecalculatesDailySummary() {
        TenantContext.setTenantId(7L);
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setTenantId(7L);
        employee.setEmployeeId("EMP0010");

        when(attendanceLogSyncService.getAttendanceLogs(eq(1L), eq(null), any(), any(), eq(0), eq(1)))
                .thenReturn(List.of(punch("emp0010", "2026-05-20T08:00:00Z")));
        when(attendanceLogSyncService.getAttendanceLogs(eq(1L), eq(null), any(), any(), eq(1), eq(1)))
                .thenReturn(List.of(punch("EMP0010", "2026-05-20T08:05:00Z")));
        when(attendanceLogSyncService.getAttendanceLogs(eq(1L), eq(null), any(), any(), eq(2), eq(1)))
                .thenReturn(List.of());

        when(attendanceLogSyncService.getAttendanceLogs(eq(2L), eq(null), any(), any(), eq(0), eq(1)))
                .thenReturn(List.of(punch("EMP0010", "2026-05-20T17:00:00Z")));
        when(attendanceLogSyncService.getAttendanceLogs(eq(2L), eq(null), any(), any(), eq(1), eq(1)))
                .thenReturn(List.of());

        when(employeeRepository.findByTenantIdAndEmployeeId(7L, "EMP0010")).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncDoorAttendance(
                1L,
                2L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 20, 23, 59),
                1
        );

        assertThat(result.getTotalPunches()).isEqualTo(3);
        assertThat(result.getCreatedLogs()).isEqualTo(2);
        assertThat(result.getRecalculatedDays()).isEqualTo(1);
        verify(attendanceCalculationService).calculateForDay(10L, LocalDate.of(2026, 5, 20));
        verify(attendanceService).generateDailySummary(10L, LocalDate.of(2026, 5, 20));
    }

    @Test
    void syncDoorAttendance_reportsUnresolvedPunchCounts() {
        when(attendanceLogSyncService.getAttendanceLogs(eq(1L), eq(null), any(), any(), eq(0), eq(200)))
                .thenReturn(List.of(punch("UNKNOWN", "2026-05-20T08:00:00Z")));
        when(attendanceLogSyncService.getAttendanceLogs(eq(2L), eq(null), any(), any(), eq(0), eq(200)))
                .thenReturn(List.of(punch("UNKNOWN", "2026-05-20T17:00:00Z")));
        when(employeeRepository.findByEmployeeId("UNKNOWN")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeIdIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncDoorAttendance(
                1L,
                2L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 20, 23, 59),
                200
        );

        assertThat(result.getSkippedEmployees()).isEqualTo(1);
        assertThat(result.getSkippedPunches()).isEqualTo(2);
        assertThat(result.getUnresolvedEmployeeNos()).containsExactly("UNKNOWN");
    }

    @Test
    void syncDoorAttendance_resolvesNumericEmployeeCodeAsPrimaryKey() {
        Employee employee = new Employee();
        employee.setId(25L);
        employee.setTenantId(1L);

        when(attendanceLogSyncService.getAttendanceLogs(eq(1L), eq(null), any(), any(), eq(0), eq(200)))
                .thenReturn(List.of(punch("25", "2026-05-20T08:00:00Z")));
        when(attendanceLogSyncService.getAttendanceLogs(eq(2L), eq(null), any(), any(), eq(0), eq(200)))
                .thenReturn(List.of(punch("25", "2026-05-20T17:00:00Z")));
        when(employeeRepository.findByEmployeeId("25")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeIdIgnoreCase("25")).thenReturn(Optional.empty());
        when(employeeRepository.findById(25L)).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoorAttendanceSyncResultDTO result = doorAttendanceSyncService.syncDoorAttendance(
                1L,
                2L,
                LocalDateTime.of(2026, 5, 20, 0, 0),
                LocalDateTime.of(2026, 5, 20, 23, 59),
                200
        );

        assertThat(result.getCreatedLogs()).isEqualTo(1);
        assertThat(result.getSkippedEmployees()).isZero();
    }

    private AttendanceLogSyncDTO.AttendanceLogEntryDTO punch(String employeeNo, String punchTime) {
        return new AttendanceLogSyncDTO.AttendanceLogEntryDTO(
                1L,
                1L,
                employeeNo,
                OffsetDateTime.parse(punchTime),
                1L
        );
    }
}

package com.hic.service;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private DailyAttendanceSummaryRepository summaryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private WorkScheduleRepository workScheduleRepository;

    @Mock
    private DateUtil dateUtil;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendanceLog testLog;
    private DailyAttendanceSummary testSummary;

    @BeforeEach
    void setUp() {
        testLog = new AttendanceLog();
        testLog.setId(1L);
        testLog.setEmployeeId(1L);
        testLog.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        testLog.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));
        testLog.setStatus("ACTIVE");

        testSummary = new DailyAttendanceSummary();
        testSummary.setId(1L);
        testSummary.setEmployeeId(1L);
        testSummary.setAttendanceDate(LocalDate.of(2024, 1, 15));
        testSummary.setAttendanceStatus(AttendanceStatus.PRESENT);
        testSummary.setHoursWorked(9.0);
    }

    @Test
    void logAttendance_validDTO_savesAndReturnsLog() {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setEmployeeId(1L);
        dto.setCheckInTime(LocalDateTime.of(2024, 1, 15, 9, 0));
        dto.setCheckOutTime(LocalDateTime.of(2024, 1, 15, 18, 0));

        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenReturn(testLog);
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(testLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(workScheduleRepository.findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(dateUtil.calculateWorkHours(any(), any())).thenReturn(9.0);
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenReturn(testSummary);

        AttendanceLogDTO result = attendanceService.logAttendance(dto);

        assertThat(result).isNotNull();
        assertThat(result.getEmployeeId()).isEqualTo(1L);
        verify(attendanceLogRepository).save(any(AttendanceLog.class));
    }

    @Test
    void generateDailySummary_withLogs_calculatesPresentStatus() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(testLog));
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(workScheduleRepository.findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(dateUtil.calculateWorkHours(any(), any())).thenReturn(9.0);
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenReturn(testSummary);

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result).isNotNull();
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void generateDailySummary_noLogs_calculatesAbsentStatus() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        DailyAttendanceSummary absentSummary = new DailyAttendanceSummary();
        absentSummary.setId(2L);
        absentSummary.setEmployeeId(1L);
        absentSummary.setAttendanceDate(date);
        absentSummary.setAttendanceStatus(AttendanceStatus.ABSENT);
        absentSummary.setHoursWorked(0.0);

        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(summaryRepository.findByEmployeeIdAndAttendanceDate(1L, date))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(DailyAttendanceSummary.class))).thenReturn(absentSummary);

        DailyAttendanceSummaryDTO result = attendanceService.generateDailySummary(1L, date);

        assertThat(result).isNotNull();
        assertThat(result.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.getHoursWorked()).isEqualTo(0.0);
    }

    @Test
    void getLogsForEmployee_returnsLogsInRange() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 31, 23, 59);

        when(attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(1L, start, end))
                .thenReturn(List.of(testLog));

        List<AttendanceLogDTO> result = attendanceService.getLogsForEmployee(1L, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
    }

    @Test
    void getDailySummary_returnsRange() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        when(summaryRepository.findByEmployeeIdAndAttendanceDateBetween(1L, start, end))
                .thenReturn(List.of(testSummary));

        List<DailyAttendanceSummaryDTO> result = attendanceService.getDailySummary(1L, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }
}

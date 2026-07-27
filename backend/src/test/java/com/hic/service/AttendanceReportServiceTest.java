package com.hic.service;

import com.hic.dto.AttendanceReportRowDTO;
import com.hic.dto.PaginatedResponse;
import com.hic.model.AttendanceLog;
import com.hic.model.Employee;
import com.hic.model.Timetable;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DepartmentRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.FaceDataRepository;
import com.hic.repository.PositionRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private FaceDataRepository faceDataRepository;
    @Mock private TimetableRepository timetableRepository;
    @Spy private AttendanceInferenceService attendanceInferenceService = new AttendanceInferenceService();

    @InjectMocks
    private AttendanceReportService attendanceReportService;

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getReport_filtersByAssignedWorkScheduleShiftType() {
        LocalDate day = LocalDate.of(2026, 7, 1);

        Employee flexibleEmployee = employee(1L, "AYK-1", "Aykhan", "Alakbarov", 10L, null);
        Employee standardEmployee = employee(2L, "STD-1", "Sara", "Standard", 11L, "STANDARD");

        Timetable flexibleTimetable = new Timetable();
        flexibleTimetable.setId(10L);
        flexibleTimetable.setShiftType("FLEXIBLE");

        Timetable standardTimetable = new Timetable();
        standardTimetable.setId(11L);
        standardTimetable.setShiftType("STANDARD");

        AttendanceLog flexibleLog = log(1L, day.atTime(9, 0), day.atTime(18, 0));
        AttendanceLog standardLog = log(2L, day.atTime(9, 0), day.atTime(18, 0));

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(flexibleLog, standardLog));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(flexibleEmployee, standardEmployee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(flexibleTimetable, standardTimetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        PaginatedResponse<AttendanceReportRowDTO> all = attendanceReportService.getReport(
                day, day, "", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> flexibleOnly = attendanceReportService.getReport(
                day, day, "FLEXIBLE", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> standardOnly = attendanceReportService.getReport(
                day, day, "STANDARD", null, null, null, null, null, null, 0, 50);
        PaginatedResponse<AttendanceReportRowDTO> nightOnly = attendanceReportService.getReport(
                day, day, "NIGHT", null, null, null, null, null, null, 0, 50);

        assertThat(all.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactlyInAnyOrder("Aykhan Alakbarov", "Sara Standard");
        assertThat(flexibleOnly.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactly("Aykhan Alakbarov");
        assertThat(standardOnly.getContent()).extracting(AttendanceReportRowDTO::getFullName)
                .containsExactly("Sara Standard");
        assertThat(nightOnly.getContent()).isEmpty();

        assertThat(flexibleOnly.getContent().get(0).getShiftType()).isEqualTo("FLEXIBLE");
    }

    @Test
    void getReport_acceptsLegacyFilterAliases() {
        LocalDate day = LocalDate.of(2026, 7, 1);
        Employee employee = employee(1L, "AYK-1", "Aykhan", "Alakbarov", 10L, "FIRST_ENTRY");

        Timetable timetable = new Timetable();
        timetable.setId(10L);
        timetable.setShiftType("FLEXIBLE");

        when(attendanceLogRepository.findByTenantIdAndCheckInTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(log(1L, day.atTime(10, 0), day.atTime(14, 0))));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(timetableRepository.findAllById(any())).thenReturn(List.of(timetable));
        when(departmentRepository.findAllById(any())).thenReturn(List.of());
        when(positionRepository.findAllById(any())).thenReturn(List.of());
        when(faceDataRepository.findTopByEmployeeIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        PaginatedResponse<AttendanceReportRowDTO> rows = attendanceReportService.getReport(
                day, day, "FIRST_ENTRY", null, null, null, null, null, null, 0, 50);

        assertThat(rows.getContent()).hasSize(1);
        assertThat(rows.getContent().get(0).getShiftType()).isEqualTo("FLEXIBLE");
    }

    private static Employee employee(Long id, String code, String first, String last, Long timetableId, String shiftType) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setTenantId(1L);
        employee.setEmployeeId(code);
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setTimetableId(timetableId);
        employee.setShiftType(shiftType);
        return employee;
    }

    private static AttendanceLog log(Long employeeId, LocalDateTime in, LocalDateTime out) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(employeeId);
        log.setTenantId(1L);
        log.setCheckInTime(in);
        log.setCheckOutTime(out);
        return log;
    }
}

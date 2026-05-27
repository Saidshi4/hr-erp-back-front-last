package com.hic.service;

import com.hic.model.AttendanceLog;
import com.hic.model.AttendanceRecord;
import com.hic.model.Employee;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.AttendanceRecordRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceCalculationService {
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayService holidayService;
    private final LeaveService leaveService;

    @Transactional
    public AttendanceRecord calculateForDay(Long employeeId, LocalDate workDate) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(AttendanceRecord::new);

        record.setEmployeeId(employeeId);
        record.setWorkDate(workDate);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            record.setTenantId(tenantId);
        } else if (record.getTenantId() == null) {
            record.setTenantId(1L);
        }
        if (employee != null) {
            record.setShiftType(employee.getShiftType());
            record.setTimetableId(employee.getTimetableId());
        }

        LocalDateTime firstEntry = getFirstEntry(employeeId, workDate);
        LocalDateTime lastExit = getLastExit(employeeId, workDate);
        record.setEntryTime(firstEntry);
        record.setExitTime(lastExit);

        int workedMinutes = 0;
        if (firstEntry != null && lastExit != null && !lastExit.isBefore(firstEntry)) {
            workedMinutes = (int) Duration.between(firstEntry, lastExit).toMinutes();
        }
        record.setWorkedMinutes(workedMinutes);
        record.setOvertimeMinutes(Math.max(workedMinutes - 8 * 60, 0));
        record.setLateMinutes(0);
        record.setEarlyLeaveMinutes(0);

        if (leaveService.hasActiveLeave(employeeId, workDate)) {
            record.setStatus("ON_LEAVE");
        } else if (holidayService.isHoliday(workDate)) {
            record.setStatus("HOLIDAY");
        } else if (firstEntry == null) {
            record.setStatus("ABSENT");
        } else {
            record.setStatus("PRESENT");
        }

        return attendanceRecordRepository.save(record);
    }

    public LocalDateTime getFirstEntry(Long employeeId, LocalDate workDate) {
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay().minusNanos(1)
        );
        return logs.stream()
                .map(AttendanceLog::getCheckInTime)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    public LocalDateTime getLastExit(Long employeeId, LocalDate workDate) {
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay().minusNanos(1)
        );
        return logs.stream()
                .map(AttendanceLog::getCheckOutTime)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    @Transactional
    public void recalculate(LocalDate start, LocalDate end, Long employeeId) {
        List<Employee> employees;
        Long tenantId = TenantContext.getTenantId();
        if (employeeId != null) {
            employees = employeeRepository.findById(employeeId).map(List::of).orElse(List.of());
        } else if (tenantId != null) {
            employees = employeeRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else {
            employees = employeeRepository.findAll();
        }

        for (Employee employee : employees) {
            LocalDate date = start;
            while (!date.isAfter(end)) {
                calculateForDay(employee.getId(), date);
                date = date.plusDays(1);
            }
        }
    }
}

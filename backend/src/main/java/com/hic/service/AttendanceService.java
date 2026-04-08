package com.hic.service;

import com.hic.dto.AttendanceDTO;
import com.hic.dto.AttendanceLogDTO;
import com.hic.dto.DailyAttendanceSummaryDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.AttendanceLog;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.DailyAttendanceSummary.AttendanceStatus;
import com.hic.model.Employee;
import com.hic.model.WorkSchedule;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DailyAttendanceSummaryRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.WorkScheduleRepository;
import com.hic.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyAttendanceSummaryRepository summaryRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final DateUtil dateUtil;

    @Transactional
    public AttendanceLogDTO logAttendance(AttendanceDTO dto) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(dto.getEmployeeId());
        log.setCheckInTime(dto.getCheckInTime());
        log.setCheckOutTime(dto.getCheckOutTime());
        log.setDeviceId(dto.getDeviceId());
        log.setDoorId(dto.getDoorId());
        log.setEventType(dto.getEventType());
        log.setVerificationMethod(dto.getVerificationMethod());
        log.setStatus("ACTIVE");
        AttendanceLog saved = attendanceLogRepository.save(log);
        if (dto.getCheckInTime() != null) {
            generateDailySummary(dto.getEmployeeId(), dto.getCheckInTime().toLocalDate());
        }
        return toLogDTO(saved);
    }

    public List<AttendanceLogDTO> getLogsForEmployee(Long employeeId, LocalDateTime start, LocalDateTime end) {
        return attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(employeeId, start, end)
                .stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    public List<AttendanceLogDTO> getLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        return attendanceLogRepository.findByCheckInTimeBetween(start, end)
                .stream().map(this::toLogDTO).collect(Collectors.toList());
    }

    @Transactional
    public DailyAttendanceSummaryDTO generateDailySummary(Long employeeId, LocalDate date) {
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeIdAndCheckInTimeBetween(
                employeeId, date.atStartOfDay(), date.atTime(23, 59, 59));

        Optional<DailyAttendanceSummary> existing = summaryRepository.findByEmployeeIdAndAttendanceDate(employeeId, date);
        DailyAttendanceSummary summary = existing.orElse(new DailyAttendanceSummary());
        summary.setEmployeeId(employeeId);
        summary.setAttendanceDate(date);
        summary.setIsHoliday(false);
        summary.setIsLeave(false);
        summary.setIsStandardDay(true);
        summary.setIsAdditionalDay(false);
        summary.setIsExtraDay(false);

        if (logs.isEmpty()) {
            summary.setAttendanceStatus(AttendanceStatus.ABSENT);
            summary.setHoursWorked(0.0);
        } else {
            LocalDateTime firstIn = logs.stream().filter(l -> l.getCheckInTime() != null)
                    .map(AttendanceLog::getCheckInTime).min(LocalDateTime::compareTo).orElse(null);
            LocalDateTime lastOut = logs.stream().filter(l -> l.getCheckOutTime() != null)
                    .map(AttendanceLog::getCheckOutTime).max(LocalDateTime::compareTo).orElse(null);
            summary.setCheckInTime(firstIn);
            summary.setCheckOutTime(lastOut);
            double hours = dateUtil.calculateWorkHours(firstIn, lastOut);
            summary.setHoursWorked(hours);

            Optional<WorkSchedule> schedule = workScheduleRepository
                    .findTopByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employeeId, date);
            int gracePeriod = schedule.map(s -> s.getGracePeriodMinutes() != null ? s.getGracePeriodMinutes() : 0).orElse(0);
            summary.setAttendanceStatus(determineStatus(firstIn, schedule.orElse(null), gracePeriod));
        }

        return toSummaryDTO(summaryRepository.save(summary));
    }

    public List<DailyAttendanceSummaryDTO> getDailySummary(Long employeeId, LocalDate start, LocalDate end) {
        return summaryRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, start, end)
                .stream().map(this::toSummaryDTO).collect(Collectors.toList());
    }

    private AttendanceStatus determineStatus(LocalDateTime checkIn, WorkSchedule schedule, int gracePeriod) {
        if (checkIn == null) return AttendanceStatus.ABSENT;
        if (schedule == null) return AttendanceStatus.PRESENT;
        // Day-of-week schedule lookup
        java.time.LocalTime start = getScheduleStart(schedule, checkIn.getDayOfWeek());
        if (start == null) return AttendanceStatus.PRESENT;
        long lateMinutes = java.time.Duration.between(start, checkIn.toLocalTime()).toMinutes();
        if (lateMinutes > gracePeriod) return AttendanceStatus.LATE;
        return AttendanceStatus.PRESENT;
    }

    private java.time.LocalTime getScheduleStart(WorkSchedule s, java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> s.getMondayStart();
            case TUESDAY -> s.getTuesdayStart();
            case WEDNESDAY -> s.getWednesdayStart();
            case THURSDAY -> s.getThursdayStart();
            case FRIDAY -> s.getFridayStart();
            case SATURDAY -> s.getSaturdayStart();
            case SUNDAY -> s.getSundayStart();
        };
    }

    private AttendanceLogDTO toLogDTO(AttendanceLog log) {
        AttendanceLogDTO dto = new AttendanceLogDTO();
        dto.setId(log.getId());
        dto.setEmployeeId(log.getEmployeeId());
        dto.setCheckInTime(log.getCheckInTime());
        dto.setCheckOutTime(log.getCheckOutTime());
        dto.setDeviceId(log.getDeviceId());
        dto.setDoorId(log.getDoorId());
        dto.setEventType(log.getEventType());
        dto.setVerificationMethod(log.getVerificationMethod());
        dto.setStatus(log.getStatus());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private DailyAttendanceSummaryDTO toSummaryDTO(DailyAttendanceSummary s) {
        DailyAttendanceSummaryDTO dto = new DailyAttendanceSummaryDTO();
        dto.setId(s.getId());
        dto.setEmployeeId(s.getEmployeeId());
        dto.setAttendanceDate(s.getAttendanceDate());
        dto.setCheckInTime(s.getCheckInTime());
        dto.setCheckOutTime(s.getCheckOutTime());
        dto.setHoursWorked(s.getHoursWorked());
        dto.setIsStandardDay(s.getIsStandardDay());
        dto.setIsAdditionalDay(s.getIsAdditionalDay());
        dto.setIsExtraDay(s.getIsExtraDay());
        dto.setIsHoliday(s.getIsHoliday());
        dto.setIsLeave(s.getIsLeave());
        dto.setAttendanceStatus(s.getAttendanceStatus());
        return dto;
    }
}

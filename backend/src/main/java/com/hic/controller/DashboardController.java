package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DashboardStatsDTO;
import com.hic.model.AttendanceLog;
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats() {
        Long tenantId = TenantContext.getTenantId();

        long totalEmployees;
        long activeEmployees;
        long onLeaveEmployees;
        long activeDevices;
        long totalDevices;
        long todayAttendance;
        long pendingLeaves;

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1).minusSeconds(1);

        if (tenantId != null) {
            totalEmployees = employeeRepository.countByTenantId(tenantId);
            activeEmployees = employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ACTIVE);
            onLeaveEmployees = employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ON_LEAVE);
            activeDevices = deviceConfigRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            totalDevices = deviceConfigRepository.countByTenantId(tenantId);
            todayAttendance = attendanceLogRepository.countByTenantIdAndCheckInTimeBetween(tenantId, todayStart, todayEnd);
            pendingLeaves = leaveRequestRepository.countByTenantIdAndStatus(tenantId, LeaveStatus.PENDING);
        } else {
            totalEmployees = employeeRepository.count();
            activeEmployees = employeeRepository.countByEmploymentStatus(EmploymentStatus.ACTIVE);
            onLeaveEmployees = employeeRepository.countByEmploymentStatus(EmploymentStatus.ON_LEAVE);
            activeDevices = deviceConfigRepository.countByStatus("ACTIVE");
            totalDevices = deviceConfigRepository.count();
            todayAttendance = attendanceLogRepository.countByCheckInTimeBetween(todayStart, todayEnd);
            pendingLeaves = leaveRequestRepository.findByStatus(LeaveStatus.PENDING).size();
        }

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .onLeaveEmployees(onLeaveEmployees)
                .activeDevices(activeDevices)
                .totalDevices(totalDevices)
                .todayAttendance(todayAttendance)
                .pendingLeaves(pendingLeaves)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        Long tenantId = TenantContext.getTenantId();

        long totalEmployees = tenantId != null
                ? employeeRepository.countByTenantId(tenantId)
                : employeeRepository.count();
        long activeEmployees = tenantId != null
                ? employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ACTIVE)
                : employeeRepository.countByEmploymentStatus(EmploymentStatus.ACTIVE);
        long onLeaveEmployees = tenantId != null
                ? employeeRepository.countByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ON_LEAVE)
                : employeeRepository.countByEmploymentStatus(EmploymentStatus.ON_LEAVE);
        long pendingLeaves = tenantId != null
                ? leaveRequestRepository.countByTenantIdAndStatus(tenantId, LeaveStatus.PENDING)
                : leaveRequestRepository.findByStatus(LeaveStatus.PENDING).size();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalEmployees", totalEmployees,
                "activeEmployees", activeEmployees,
                "onLeaveEmployees", onLeaveEmployees,
                "pendingLeaves", pendingLeaves
        )));
    }

    @GetMapping("/device-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceStatus() {
        Long tenantId = TenantContext.getTenantId();
        long totalDevices = tenantId != null
                ? deviceConfigRepository.countByTenantId(tenantId)
                : deviceConfigRepository.count();
        long onlineDevices = tenantId != null
                ? deviceConfigRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")
                : deviceConfigRepository.countByStatus("ACTIVE");
        long offlineDevices = totalDevices - onlineDevices;

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalDevices", totalDevices,
                "onlineDevices", onlineDevices,
                "offlineDevices", offlineDevices
        )));
    }

    @GetMapping("/access-logs/latest")
    public ResponseEntity<ApiResponse<List<AttendanceLog>>> getLatestAccessLogs() {
        Long tenantId = TenantContext.getTenantId();
        List<AttendanceLog> latest = tenantId != null
                ? attendanceLogRepository.findByTenantIdOrderByCheckInTimeDesc(tenantId, PageRequest.of(0, 10))
                : attendanceLogRepository.findAllByOrderByCheckInTimeDesc(PageRequest.of(0, 10));
        return ResponseEntity.ok(ApiResponse.success(latest));
    }

    @GetMapping("/current-time")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "datetime", now.toString(),
                "formatted", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        )));
    }
}


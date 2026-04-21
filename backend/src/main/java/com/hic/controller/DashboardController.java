package com.hic.controller;

import com.hic.dto.ApiResponse;
import com.hic.dto.DashboardStatsDTO;
import com.hic.model.Employee.EmploymentStatus;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.repository.AttendanceLogRepository;
import com.hic.repository.DeviceConfigRepository;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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
            activeEmployees = employeeRepository.findByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ACTIVE).size();
            onLeaveEmployees = employeeRepository.findByTenantIdAndEmploymentStatus(tenantId, EmploymentStatus.ON_LEAVE).size();
            activeDevices = deviceConfigRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            totalDevices = deviceConfigRepository.findByTenantId(tenantId).size();
            todayAttendance = attendanceLogRepository.countByTenantIdAndCheckInTimeBetween(tenantId, todayStart, todayEnd);
            pendingLeaves = leaveRequestRepository.countByTenantIdAndStatus(tenantId, LeaveStatus.PENDING);
        } else {
            totalEmployees = employeeRepository.count();
            activeEmployees = employeeRepository.findByEmploymentStatus(EmploymentStatus.ACTIVE).size();
            onLeaveEmployees = employeeRepository.findByEmploymentStatus(EmploymentStatus.ON_LEAVE).size();
            activeDevices = deviceConfigRepository.findByStatus("ACTIVE").size();
            totalDevices = deviceConfigRepository.count();
            todayAttendance = attendanceLogRepository.findByCheckInTimeBetween(todayStart, todayEnd).size();
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
}

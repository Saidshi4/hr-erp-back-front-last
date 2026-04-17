package com.hic.service;

import com.hic.dto.ReportDTO;
import com.hic.model.DailyAttendanceSummary;
import com.hic.model.Department;
import com.hic.model.Employee;
import com.hic.model.LeaveRequest;
import com.hic.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final DailyAttendanceSummaryRepository summaryRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public List<ReportDTO.AttendanceReportDTO> getAttendanceReport(LocalDate start, LocalDate end, Long branchId, Long departmentId) {
        List<Employee> employees = getFilteredEmployees(branchId, departmentId);
        List<ReportDTO.AttendanceReportDTO> result = new ArrayList<>();

        for (Employee emp : employees) {
            List<DailyAttendanceSummary> summaries = summaryRepository.findByEmployeeIdAndAttendanceDateBetween(emp.getId(), start, end);
            long presentDays = summaries.stream().filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.PRESENT || s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.LATE).count();
            long absentDays = summaries.stream().filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.ABSENT).count();
            long lateDays = summaries.stream().filter(s -> s.getAttendanceStatus() == DailyAttendanceSummary.AttendanceStatus.LATE).count();
            double totalHours = summaries.stream().mapToDouble(s -> s.getHoursWorked() != null ? s.getHoursWorked() : 0.0).sum();

            ReportDTO.AttendanceReportDTO dto = new ReportDTO.AttendanceReportDTO();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
            dto.setPresentDays((int) presentDays);
            dto.setAbsentDays((int) absentDays);
            dto.setLateDays((int) lateDays);
            dto.setTotalHoursWorked(totalHours);
            result.add(dto);
        }
        return result;
    }

    public List<ReportDTO.LeaveReportDTO> getLeaveReport(LocalDate start, LocalDate end, Long branchId) {
        List<Employee> employees = getFilteredEmployees(branchId, null);
        List<Long> empIds = employees.stream().map(Employee::getId).collect(Collectors.toList());

        return leaveRequestRepository.findByEmployeeIdsAndDateRange(empIds, start, end)
                .stream()
                .map(lr -> {
                    ReportDTO.LeaveReportDTO dto = new ReportDTO.LeaveReportDTO();
                    dto.setLeaveRequestId(lr.getId());
                    dto.setEmployeeId(lr.getEmployeeId());
                    employees.stream().filter(e -> e.getId().equals(lr.getEmployeeId())).findFirst()
                            .ifPresent(e -> dto.setEmployeeName(e.getFirstName() + " " + e.getLastName()));
                    leaveTypeRepository.findById(lr.getLeaveTypeId())
                            .ifPresent(lt -> dto.setLeaveTypeName(lt.getLeaveName()));
                    dto.setStartDate(lr.getStartDate());
                    dto.setEndDate(lr.getEndDate());
                    dto.setStatus(lr.getStatus() != null ? lr.getStatus().name() : null);
                    return dto;
                }).collect(Collectors.toList());
    }

    public List<ReportDTO.EmployeeSummaryDTO> getEmployeeSummary(Long branchId, Long departmentId) {
        List<Employee> employees = getFilteredEmployees(branchId, departmentId);
        return employees.stream().map(emp -> {
            ReportDTO.EmployeeSummaryDTO dto = new ReportDTO.EmployeeSummaryDTO();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
            dto.setEmploymentStatus(emp.getEmploymentStatus() != null ? emp.getEmploymentStatus().name() : null);
            if (emp.getDepartmentId() != null) {
                departmentRepository.findById(emp.getDepartmentId()).ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private List<Employee> getFilteredEmployees(Long branchId, Long departmentId) {
        if (departmentId != null) {
            return employeeRepository.findByDepartmentId(departmentId);
        }
        if (branchId != null) {
            List<Department> depts = departmentRepository.findByBranchId(branchId);
            List<Long> deptIds = depts.stream().map(Department::getId).collect(Collectors.toList());
            if (deptIds.isEmpty()) return new ArrayList<>();
            return employeeRepository.findByDepartmentIdIn(deptIds, org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        return employeeRepository.findAll();
    }
}

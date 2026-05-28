package com.hic.service;

import com.hic.dto.EmployeeShiftAssignmentDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Employee;
import com.hic.model.EmployeeShiftAssignment;
import com.hic.model.Timetable;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.EmployeeShiftAssignmentRepository;
import com.hic.repository.TimetableRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftAssignmentService {

    private final EmployeeShiftAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final TimetableRepository timetableRepository;

    @Transactional
    public EmployeeShiftAssignmentDTO assignEmployeeToShift(Long employeeId, Long timetableId, LocalDate startDate, LocalDate endDate) {
        Long tenantId = requireTenant();
        validateDateRange(startDate, endDate);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        ensureSameTenant(tenantId, employee.getTenantId(), "Employee");
        ensureSameTenant(tenantId, timetable.getTenantId(), "Timetable");

        if (employee.getEmploymentStatus() != Employee.EmploymentStatus.ACTIVE) {
            throw new BadRequestException("Only active employees can be assigned to shifts");
        }

        List<EmployeeShiftAssignment> overlaps = assignmentRepository.findOverlappingAssignments(tenantId, employeeId, startDate, endDate, null);
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Employee already has an overlapping active shift assignment");
        }

        EmployeeShiftAssignment assignment = new EmployeeShiftAssignment();
        assignment.setTenantId(tenantId);
        assignment.setEmployeeId(employeeId);
        assignment.setTimetableId(timetableId);
        assignment.setEffectiveStartDate(startDate);
        assignment.setEffectiveEndDate(endDate);
        assignment.setAssignedBy(TenantContext.getUserId());
        assignment.setStatus(EmployeeShiftAssignment.Status.ACTIVE);

        EmployeeShiftAssignment saved = assignmentRepository.save(assignment);

        employee.setTimetableId(timetableId);
        employee.setShiftType(timetable.getShiftType());
        employeeRepository.save(employee);

        return toDTO(saved);
    }

    @Transactional
    public EmployeeShiftAssignmentDTO updateAssignment(Long id, LocalDate startDate, LocalDate endDate) {
        Long tenantId = requireTenant();
        validateDateRange(startDate, endDate);

        EmployeeShiftAssignment assignment = findByIdAndTenant(id, tenantId);
        List<EmployeeShiftAssignment> overlaps = assignmentRepository.findOverlappingAssignments(
                tenantId,
                assignment.getEmployeeId(),
                startDate,
                endDate,
                id
        );
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Updated dates overlap with another active assignment");
        }

        assignment.setEffectiveStartDate(startDate);
        assignment.setEffectiveEndDate(endDate);
        return toDTO(assignmentRepository.save(assignment));
    }

    @Transactional
    public void removeEmployeeFromShift(Long assignmentId) {
        Long tenantId = requireTenant();
        EmployeeShiftAssignment assignment = findByIdAndTenant(assignmentId, tenantId);
        assignment.setStatus(EmployeeShiftAssignment.Status.INACTIVE);
        if (assignment.getEffectiveEndDate() == null || assignment.getEffectiveEndDate().isAfter(LocalDate.now())) {
            assignment.setEffectiveEndDate(LocalDate.now());
        }
        assignmentRepository.save(assignment);
    }

    public List<EmployeeShiftAssignmentDTO> getAll() {
        Long tenantId = requireTenant();
        return assignmentRepository.findByTenantId(tenantId).stream().map(this::toDTO).toList();
    }

    public List<EmployeeShiftAssignmentDTO> getEmployeesForShift(Long timetableId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return assignmentRepository.findActiveByTimetableAndDate(tenantId, timetableId, targetDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public EmployeeShiftAssignmentDTO getActiveShiftForEmployee(Long employeeId, LocalDate date) {
        Long tenantId = requireTenant();
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return assignmentRepository.findActiveByEmployeeAndDate(tenantId, employeeId, targetDate)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<EmployeeShiftAssignmentDTO> getShiftHistory(Long employeeId) {
        Long tenantId = requireTenant();
        return assignmentRepository.findByTenantIdAndEmployeeIdOrderByEffectiveStartDateDesc(tenantId, employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<EmployeeShiftAssignmentDTO> bulkAssignToShift(List<Long> employeeIds, Long timetableId, LocalDate startDate, LocalDate endDate) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BadRequestException("At least one employee is required");
        }
        List<EmployeeShiftAssignmentDTO> result = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            try {
                result.add(assignEmployeeToShift(employeeId, timetableId, startDate, endDate));
            } catch (RuntimeException ignored) {
                // continue processing remaining employees
            }
        }
        if (result.isEmpty()) {
            throw new BadRequestException("No employee could be assigned in bulk operation");
        }
        return result;
    }

    private EmployeeShiftAssignment findByIdAndTenant(Long id, Long tenantId) {
        EmployeeShiftAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftAssignment", id));
        ensureSameTenant(tenantId, assignment.getTenantId(), "ShiftAssignment");
        return assignment;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BadRequestException("Start date is required");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("End date must be on or after start date");
        }
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }

    private void ensureSameTenant(Long expectedTenantId, Long actualTenantId, String resourceName) {
        if (!expectedTenantId.equals(actualTenantId)) {
            throw new BadRequestException(resourceName + " does not belong to current tenant");
        }
    }

    private EmployeeShiftAssignmentDTO toDTO(EmployeeShiftAssignment assignment) {
        EmployeeShiftAssignmentDTO dto = new EmployeeShiftAssignmentDTO();
        dto.setId(assignment.getId());
        dto.setTenantId(assignment.getTenantId());
        dto.setEmployeeId(assignment.getEmployeeId());
        dto.setTimetableId(assignment.getTimetableId());
        dto.setEffectiveStartDate(assignment.getEffectiveStartDate());
        dto.setEffectiveEndDate(assignment.getEffectiveEndDate());
        dto.setAssignedBy(assignment.getAssignedBy());
        dto.setAssignedAt(assignment.getAssignedAt());
        dto.setStatus(assignment.getStatus());
        return dto;
    }
}

package com.hic.service;

import com.hic.dto.LeaveRequestDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.LeaveRequest;
import com.hic.model.LeaveRequest.LeaveStatus;
import com.hic.model.LeaveType;
import com.hic.repository.EmployeeRepository;
import com.hic.repository.LeaveRequestRepository;
import com.hic.repository.LeaveTypeRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public List<LeaveRequestDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<LeaveRequest> requests = tenantId != null
                ? leaveRequestRepository.findByTenantId(tenantId)
                : leaveRequestRepository.findAll();
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveType> getAllLeaveTypes() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null
                ? leaveTypeRepository.findByTenantId(tenantId)
                : leaveTypeRepository.findAll();
    }

    public List<LeaveRequestDTO> getByEmployee(Long employeeId) {
        Long tenantId = TenantContext.getTenantId();
        List<LeaveRequest> requests = tenantId != null
                ? leaveRequestRepository.findByTenantIdAndEmployeeId(tenantId, employeeId)
                : leaveRequestRepository.findByEmployeeId(employeeId);
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public LeaveRequestDTO getById(Long id) {
        return toDTO(leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id)));
    }

    public boolean hasActiveLeave(Long employeeId, LocalDate date) {
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, LeaveStatus.APPROVED);
        return requests.stream().anyMatch(request ->
                !date.isBefore(request.getStartDate()) && !date.isAfter(request.getEndDate()));
    }

    @Transactional
    public LeaveRequestDTO create(LeaveRequestDTO dto) {
        if (!employeeRepository.existsById(dto.getEmployeeId())) {
            throw new ResourceNotFoundException("Employee", dto.getEmployeeId());
        }
        if (!leaveTypeRepository.existsById(dto.getLeaveTypeId())) {
            throw new ResourceNotFoundException("LeaveType", dto.getLeaveTypeId());
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        LeaveRequest req = new LeaveRequest();
        req.setEmployeeId(dto.getEmployeeId());
        req.setLeaveTypeId(dto.getLeaveTypeId());
        req.setStartDate(dto.getStartDate());
        req.setEndDate(dto.getEndDate());
        req.setStatus(LeaveStatus.PENDING);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            req.setTenantId(tenantId);
        }
        return toDTO(leaveRequestRepository.save(req));
    }

    @Transactional
    public LeaveRequestDTO updateStatus(Long id, LeaveStatus status, Long approvedBy) {
        LeaveRequest req = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id));
        req.setStatus(status);
        if (status == LeaveStatus.APPROVED) {
            req.setApprovedBy(approvedBy != null ? approvedBy : TenantContext.getUserId());
            req.setApprovalDate(LocalDate.now());
        }
        return toDTO(leaveRequestRepository.save(req));
    }

    @Transactional
    public void delete(Long id) {
        if (!leaveRequestRepository.existsById(id)) {
            throw new ResourceNotFoundException("LeaveRequest", id);
        }
        leaveRequestRepository.deleteById(id);
    }

    private LeaveRequestDTO toDTO(LeaveRequest req) {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setId(req.getId());
        dto.setEmployeeId(req.getEmployeeId());
        dto.setLeaveTypeId(req.getLeaveTypeId());
        dto.setStartDate(req.getStartDate());
        dto.setEndDate(req.getEndDate());
        dto.setStatus(req.getStatus());
        dto.setApprovedBy(req.getApprovedBy());
        dto.setApprovalDate(req.getApprovalDate());
        dto.setCreatedAt(req.getCreatedAt());
        return dto;
    }
}

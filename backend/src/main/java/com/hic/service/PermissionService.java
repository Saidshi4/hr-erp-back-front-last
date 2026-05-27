package com.hic.service;

import com.hic.dto.PermissionDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Leave;
import com.hic.repository.LeaveRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final LeaveRepository leaveRepository;

    public List<PermissionDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<Leave> leaves = tenantId == null
                ? leaveRepository.findAll()
                : leaveRepository.findByTenantId(tenantId);
        return leaves.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PermissionDTO getById(Long id) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
        return toDTO(leave);
    }

    @Transactional
    public PermissionDTO create(PermissionDTO dto) {
        Leave leave = toEntity(dto);
        if (leave.getTenantId() == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                leave.setTenantId(tenantId);
            }
        }
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public PermissionDTO update(Long id, PermissionDTO dto) {
        Leave existing = leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
        Leave leave = toEntity(dto);
        leave.setId(existing.getId());
        if (leave.getTenantId() == null) {
            leave.setTenantId(existing.getTenantId());
        }
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public void delete(Long id) {
        if (!leaveRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission", id);
        }
        leaveRepository.deleteById(id);
    }

    private PermissionDTO toDTO(Leave leave) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(leave.getId());
        dto.setTenantId(leave.getTenantId());
        dto.setName(leave.getName());
        dto.setDescription(leave.getDescription());
        dto.setLeaveType(leave.getLeaveType());
        dto.setApplyType(leave.getApplyType());
        dto.setTargetId(leave.getTargetId());
        dto.setStartDate(leave.getStartDate() != null ? leave.getStartDate().toString() : null);
        dto.setEndDate(leave.getEndDate() != null ? leave.getEndDate().toString() : null);
        dto.setStatus(leave.getStatus());
        return dto;
    }

    private Leave toEntity(PermissionDTO dto) {
        Leave leave = new Leave();
        leave.setTenantId(dto.getTenantId());
        leave.setName(dto.getName());
        leave.setDescription(dto.getDescription());
        leave.setLeaveType(dto.getLeaveType() != null ? dto.getLeaveType() : "MEDICAL_LEAVE");
        leave.setApplyType(dto.getApplyType() != null ? dto.getApplyType() : "EMPLOYEE");
        leave.setTargetId(dto.getTargetId());
        leave.setStartDate(dto.getStartDate() != null ? LocalDate.parse(dto.getStartDate()) : LocalDate.now());
        leave.setEndDate(dto.getEndDate() != null ? LocalDate.parse(dto.getEndDate()) : LocalDate.now());
        leave.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        return leave;
    }
}

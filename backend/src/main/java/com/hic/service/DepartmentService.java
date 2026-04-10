package com.hic.service;

import com.hic.dto.DepartmentDTO;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Department;
import com.hic.repository.BranchRepository;
import com.hic.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;

    public List<DepartmentDTO> getAll() {
        return departmentRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<DepartmentDTO> getByBranch(Long branchId) {
        return departmentRepository.findByBranchId(branchId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public DepartmentDTO getById(Long id) {
        return toDTO(departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id)));
    }

    @Transactional
    public DepartmentDTO create(DepartmentDTO dto) {
        if (!branchRepository.existsById(dto.getBranchId())) {
            throw new ResourceNotFoundException("Branch", dto.getBranchId());
        }
        Department dept = new Department();
        dept.setDepartmentName(dto.getDepartmentName());
        dept.setBranchId(dto.getBranchId());
        return toDTO(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentDTO update(Long id, DepartmentDTO dto) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
        dept.setDepartmentName(dto.getDepartmentName());
        if (dto.getBranchId() != null) dept.setBranchId(dto.getBranchId());
        return toDTO(departmentRepository.save(dept));
    }

    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Department", id);
        }
        departmentRepository.deleteById(id);
    }

    private DepartmentDTO toDTO(Department dept) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(dept.getId());
        dto.setDepartmentName(dept.getDepartmentName());
        dto.setBranchId(dept.getBranchId());
        dto.setCreatedAt(dept.getCreatedAt());
        return dto;
    }
}

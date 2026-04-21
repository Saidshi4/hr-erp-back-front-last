package com.hic.service;

import com.hic.dto.BranchDTO;
import com.hic.exception.BadRequestException;
import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Branch;
import com.hic.repository.BranchRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<BranchDTO> getAll() {
        Long tenantId = TenantContext.getTenantId();
        List<Branch> branches = tenantId != null
                ? branchRepository.findByTenantId(tenantId)
                : branchRepository.findAll();
        return branches.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public BranchDTO getById(Long id) {
        return toDTO(branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id)));
    }

    @Transactional
    public BranchDTO create(BranchDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            branchRepository.findByTenantIdAndBranchCode(tenantId, dto.getBranchCode()).ifPresent(b -> {
                throw new BadRequestException("Branch code already exists: " + dto.getBranchCode());
            });
        } else if (branchRepository.findByBranchCode(dto.getBranchCode()).isPresent()) {
            throw new BadRequestException("Branch code already exists: " + dto.getBranchCode());
        }
        Branch branch = new Branch();
        branch.setBranchName(dto.getBranchName());
        branch.setBranchCode(dto.getBranchCode());
        branch.setLocation(dto.getLocation());
        branch.setIsHeadOffice(dto.getIsHeadOffice() != null ? dto.getIsHeadOffice() : false);
        if (tenantId != null) {
            branch.setTenantId(tenantId);
        }
        return toDTO(branchRepository.save(branch));
    }

    @Transactional
    public BranchDTO update(Long id, BranchDTO dto) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
        branch.setBranchName(dto.getBranchName());
        branch.setLocation(dto.getLocation());
        branch.setIsHeadOffice(dto.getIsHeadOffice() != null ? dto.getIsHeadOffice() : branch.getIsHeadOffice());
        return toDTO(branchRepository.save(branch));
    }

    @Transactional
    public void delete(Long id) {
        if (!branchRepository.existsById(id)) {
            throw new ResourceNotFoundException("Branch", id);
        }
        branchRepository.deleteById(id);
    }

    private BranchDTO toDTO(Branch branch) {
        BranchDTO dto = new BranchDTO();
        dto.setId(branch.getId());
        dto.setBranchName(branch.getBranchName());
        dto.setBranchCode(branch.getBranchCode());
        dto.setLocation(branch.getLocation());
        dto.setIsHeadOffice(branch.getIsHeadOffice());
        return dto;
    }
}

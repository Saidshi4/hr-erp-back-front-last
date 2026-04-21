package com.hic.repository;

import com.hic.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    // Tenant-aware methods
    List<Branch> findByTenantId(Long tenantId);
    Optional<Branch> findByTenantIdAndBranchCode(Long tenantId, String branchCode);
    long countByTenantId(Long tenantId);

    // Legacy methods
    Optional<Branch> findByBranchCode(String branchCode);
    boolean existsByBranchCode(String branchCode);
}

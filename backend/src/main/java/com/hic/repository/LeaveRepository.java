package com.hic.repository;

import com.hic.model.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByTenantId(Long tenantId);
    List<Leave> findByTenantIdAndApplyType(Long tenantId, String applyType);
    List<Leave> findByTenantIdAndTargetId(Long tenantId, Long targetId);
    List<Leave> findByTenantIdAndStatus(Long tenantId, String status);
}

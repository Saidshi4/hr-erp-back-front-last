package com.hic.repository;

import com.hic.model.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {
    List<DeviceConfig> findByBranchId(Long branchId);
    List<DeviceConfig> findByStatus(String status);
    Optional<DeviceConfig> findByDeviceId(String deviceId);
}

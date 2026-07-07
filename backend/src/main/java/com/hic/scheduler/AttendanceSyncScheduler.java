package com.hic.scheduler;

import com.hic.model.Tenant;
import com.hic.repository.TenantRepository;
import com.hic.service.DoorAttendanceSyncService;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceSyncScheduler {

    private final TenantRepository tenantRepository;
    private final DoorAttendanceSyncService doorAttendanceSyncService;

    // Run every 2 minutes (120,000 milliseconds)
    @Scheduled(fixedDelay = 120000)
    public void syncAllTenantsDevices() {
        log.info("Starting background attendance log sync for all tenants...");
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(3);

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                log.debug("Syncing attendance devices for tenant: {} ({})", tenant.getCompanyName(), tenant.getId());
                doorAttendanceSyncService.syncAllDevices(start, end, 1000);
            } catch (Exception e) {
                log.error("Failed to sync attendance for tenant id: " + tenant.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Background attendance log sync completed.");
    }
}

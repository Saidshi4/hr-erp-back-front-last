package com.hic.service;

import com.hic.exception.ResourceNotFoundException;
import com.hic.model.Holiday;
import com.hic.repository.HolidayRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HolidayService {
    private final HolidayRepository holidayRepository;

    public List<Holiday> getAll() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return holidayRepository.findAll();
        }
        return holidayRepository.findByTenantId(tenantId);
    }

    public Holiday getById(Long id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
    }

    @Transactional
    public Holiday create(Holiday holiday) {
        if (holiday.getTenantId() == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                holiday.setTenantId(tenantId);
            }
        }
        return holidayRepository.save(holiday);
    }

    @Transactional
    public Holiday update(Long id, Holiday holiday) {
        Holiday existing = getById(id);
        holiday.setId(existing.getId());
        if (holiday.getTenantId() == null) {
            holiday.setTenantId(existing.getTenantId());
        }
        return holidayRepository.save(holiday);
    }

    @Transactional
    public void delete(Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday", id);
        }
        holidayRepository.deleteById(id);
    }

    public boolean isHoliday(LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return holidayRepository.existsByTenantIdAndHolidayDate(tenantId, date);
        }
        return holidayRepository.existsByHolidayDate(date);
    }
}

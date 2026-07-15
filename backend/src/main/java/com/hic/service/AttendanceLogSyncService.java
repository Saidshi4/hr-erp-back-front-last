package com.hic.service;

import com.hic.dto.AttendanceLogSyncDTO;
import com.hic.exception.DeviceSyncException;
import com.hic.exception.UpstreamApiException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.hic.repository.EmployeeRepository;
import com.hic.model.Employee;
import com.hic.util.TenantContext;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AttendanceLogSyncService {

    private final RestTemplate restTemplate;
    private final EmployeeRepository employeeRepository;

    @Value("${isapi.base-url}")
    private String isapiBaseUrl;

    public List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> getAttendanceLogs(
            Long deviceId,
            String employeeNo,
            Integer limit
    ) {
        java.net.URI url = UriComponentsBuilder
                .fromHttpUrl(basePunchesUrl())
                .queryParamIfPresent("deviceId", Optional.ofNullable(deviceId))
                .queryParamIfPresent("employeeNo", Optional.ofNullable(normalizeToNull(employeeNo)))
                .queryParamIfPresent("limit", Optional.ofNullable(limit))
                .build().toUri();

        List<IsapiPunchResponse> punches = exchangeForList(url);
        return mapPunches(punches);
    }

    public List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> getAttendanceLogs(
            Long deviceId,
            String employeeNo,
            OffsetDateTime start,
            OffsetDateTime end,
            Integer page,
            Integer size
    ) {
        java.net.URI url = UriComponentsBuilder
                .fromHttpUrl(basePunchesUrl())
                .queryParamIfPresent("deviceId", Optional.ofNullable(deviceId))
                .queryParamIfPresent("employeeNo", Optional.ofNullable(normalizeToNull(employeeNo)))
                .queryParam("start", start != null ? start.toInstant().toString() : null)
                .queryParam("end", end != null ? end.toInstant().toString() : null)
                .queryParamIfPresent("page", Optional.ofNullable(page))
                .queryParamIfPresent("size", Optional.ofNullable(size))
                .build().toUri();

        List<IsapiPunchResponse> punches = exchangeForList(url);
        return mapPunches(punches);
    }

    private List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> mapPunches(List<IsapiPunchResponse> punches) {
        if (punches == null || punches.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> employeeNos = punches.stream()
                .map(IsapiPunchResponse::getEmployeeNo)
                .filter(org.springframework.util.StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        Map<String, Employee> employeeMap = new HashMap<>();
        if (!employeeNos.isEmpty()) {
            Long tenantId = TenantContext.getTenantId();
            List<Employee> employees = tenantId != null
                    ? employeeRepository.findByTenantIdAndEmployeeIdIn(tenantId, employeeNos)
                    : employeeRepository.findByEmployeeIdIn(employeeNos);
            for (Employee emp : employees) {
                indexEmployee(employeeMap, emp);
            }
            for (String no : employeeNos) {
                if (employeeMap.containsKey(no.toLowerCase())) {
                    continue;
                }
                List<Employee> byDeviceNo = tenantId != null
                        ? employeeRepository.findByTenantIdAndDeviceEmployeeNoIgnoreCase(tenantId, no)
                        : employeeRepository.findByDeviceEmployeeNoIgnoreCase(no);
                if (byDeviceNo.size() == 1) {
                    indexEmployee(employeeMap, byDeviceNo.get(0));
                }
            }
        }

        return punches.stream()
                .map(response -> {
                    String key = response.getEmployeeNo() != null ? response.getEmployeeNo().trim().toLowerCase() : null;
                    Employee emp = key != null ? employeeMap.get(key) : null;
                    String firstName = emp != null ? emp.getFirstName() : null;
                    String lastName = emp != null ? emp.getLastName() : null;
                    return new AttendanceLogSyncDTO.AttendanceLogEntryDTO(
                            response.getId(),
                            response.getDeviceId(),
                            response.getEmployeeNo(),
                            response.getPunchTime(),
                            response.getRawEventId(),
                            firstName,
                            lastName
                    );
                })
                .toList();
    }

    private static void indexEmployee(Map<String, Employee> employeeMap, Employee emp) {
        if (emp.getEmployeeId() != null) {
            employeeMap.put(emp.getEmployeeId().trim().toLowerCase(), emp);
        }
        if (emp.getDeviceEmployeeNo() != null && !emp.getDeviceEmployeeNo().isBlank()) {
            employeeMap.putIfAbsent(emp.getDeviceEmployeeNo().trim().toLowerCase(), emp);
        }
    }



    private String basePunchesUrl() {
        return trimTrailingSlash(isapiBaseUrl) + "/api/punches";
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizeToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<IsapiPunchResponse> exchangeForList(java.net.URI url) {
        try {
            ResponseEntity<List<IsapiPunchResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new UpstreamApiException(ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new DeviceSyncException("ISAPI service is unavailable");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class IsapiPunchResponse {
        private Long id;
        private Long deviceId;
        private String employeeNo;
        private OffsetDateTime punchTime;
        private Long rawEventId;
    }
}

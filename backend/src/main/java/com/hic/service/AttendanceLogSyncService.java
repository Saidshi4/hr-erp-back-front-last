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

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceLogSyncService {

    private final RestTemplate restTemplate;

    @Value("${isapi.base-url}")
    private String isapiBaseUrl;

    public List<AttendanceLogSyncDTO.AttendanceLogEntryDTO> getAttendanceLogs(
            Long deviceId,
            String employeeNo,
            Integer limit
    ) {
        String url = UriComponentsBuilder
                .fromHttpUrl(basePunchesUrl())
                .queryParamIfPresent("deviceId", Optional.ofNullable(deviceId))
                .queryParamIfPresent("employeeNo", Optional.ofNullable(normalizeToNull(employeeNo)))
                .queryParamIfPresent("limit", Optional.ofNullable(limit))
                .toUriString();

        List<IsapiPunchResponse> punches = exchangeForList(url);
        return punches.stream()
                .map(this::toAttendanceLogEntry)
                .toList();
    }

    private AttendanceLogSyncDTO.AttendanceLogEntryDTO toAttendanceLogEntry(IsapiPunchResponse response) {
        return new AttendanceLogSyncDTO.AttendanceLogEntryDTO(
                response.getId(),
                response.getDeviceId(),
                response.getEmployeeNo(),
                response.getPunchTime(),
                response.getRawEventId()
        );
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

    private List<IsapiPunchResponse> exchangeForList(String url) {
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

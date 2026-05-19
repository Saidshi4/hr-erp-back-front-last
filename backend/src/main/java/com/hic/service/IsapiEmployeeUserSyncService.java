package com.hic.service;

import com.hic.dto.IsapiUserInfoCreateRequestDTO;
import com.hic.exception.DeviceSyncException;
import com.hic.exception.UpstreamApiException;
import com.hic.model.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IsapiEmployeeUserSyncService {

    private final RestTemplate restTemplate;

    @Value("${isapi.base-url}")
    private String isapiBaseUrl;

    @Value("${isapi.user-info-record.path:/ISAPI/AccessControl/UserInfo/Record}")
    private String userInfoRecordPath;

    @Value("${isapi.user-info-record.security:1}")
    private String security;

    @Value("${isapi.user-info-record.iv:}")
    private String iv;

    @Value("${isapi.user-info-record.door-right:1}")
    private String doorRight;

    @Value("${isapi.user-info-record.door-no:1}")
    private int doorNo;

    @Value("${isapi.user-info-record.plan-template-no:1}")
    private String planTemplateNo;

    @Value("${isapi.user-info-record.username:}")
    private String username;

    @Value("${isapi.user-info-record.password:}")
    private String password;

    public void syncEmployee(Employee employee) {
        IsapiUserInfoCreateRequestDTO request = buildRequest(employee);
        HttpHeaders headers = buildHeaders();
        String url = buildUserInfoRecordUrl();

        try {
            HttpEntity<IsapiUserInfoCreateRequestDTO> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new UpstreamApiException(response.getStatusCode(), response.getBody());
            }
        } catch (HttpStatusCodeException ex) {
            throw new UpstreamApiException(ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new DeviceSyncException("ISAPI user sync is unavailable");
        }
    }

    private IsapiUserInfoCreateRequestDTO buildRequest(Employee employee) {
        LocalDate beginDate = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now();
        LocalDateTime beginTime = beginDate.atStartOfDay();
        LocalDateTime endTime = beginTime.plusYears(10).minusSeconds(1);
        String fullName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        IsapiUserInfoCreateRequestDTO.ValidityDTO validity = new IsapiUserInfoCreateRequestDTO.ValidityDTO(
                true,
                beginTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "local"
        );
        IsapiUserInfoCreateRequestDTO.RightPlanDTO rightPlan = new IsapiUserInfoCreateRequestDTO.RightPlanDTO(doorNo, planTemplateNo);
        IsapiUserInfoCreateRequestDTO.UserInfoDTO userInfo = new IsapiUserInfoCreateRequestDTO.UserInfoDTO(
                employee.getEmployeeId(),
                fullName,
                "normal",
                employee.getGender() == null ? "" : employee.getGender(),
                false,
                0,
                validity,
                doorRight,
                List.of(rightPlan),
                ""
        );
        return new IsapiUserInfoCreateRequestDTO(userInfo);
    }

    private String buildUserInfoRecordUrl() {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(isapiBaseUrl) + userInfoRecordPath)
                .queryParam("format", "json")
                .queryParam("security", security);
        if (StringUtils.hasText(iv)) {
            builder.queryParam("iv", iv);
        }
        return builder.toUriString();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            String token = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
        }
        return headers;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

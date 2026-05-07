package com.hic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IsapiClientServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private IsapiClientService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "isapiBaseUrl", "http://isapi:8081");
        ReflectionTestUtils.setField(service, "deviceEnabledDefault", false);
    }

    // -----------------------------------------------------------------------
    // checkDeviceConnectivity
    // -----------------------------------------------------------------------

    @Test
    void checkDeviceConnectivity_deviceAlreadyInIsapi_checksStatusDirectly() {
        List<Map<String, Object>> devices = List.of(Map.of("id", 1, "ip", "10.0.0.1"));
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices"),
                eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(devices));

        when(restTemplate.getForObject(
                eq("http://isapi:8081/api/devices/1/status"), eq(Map.class)))
                .thenReturn(Map.of("online", true));

        boolean result = service.checkDeviceConnectivity("10.0.0.1", 80, "admin", "pass");

        assertThat(result).isTrue();
        verify(restTemplate, never()).postForObject(contains("/api/devices"), any(), eq(Map.class));
    }

    @Test
    void checkDeviceConnectivity_deviceNotInIsapi_registersAndChecksStatus() {
        // first call: device not found
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices"),
                eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        // register returns id=5
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("id", 5, "ip", "10.0.0.2"));

        when(restTemplate.getForObject(
                eq("http://isapi:8081/api/devices/5/status"), eq(Map.class)))
                .thenReturn(Map.of("online", false));

        boolean result = service.checkDeviceConnectivity("10.0.0.2", 80, "admin", "pass");

        assertThat(result).isFalse();
    }

    @Test
    void checkDeviceConnectivity_isapiUnavailable_returnsFalse() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connection refused"));

        boolean result = service.checkDeviceConnectivity("10.0.0.3", 80, "admin", "pass");

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // registerDeviceInIsapi – JSON Content-Type header must be set
    // -----------------------------------------------------------------------

    @Test
    void checkDeviceConnectivity_registrationUsesJsonContentType() {
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices"),
                eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices"), captor.capture(), eq(Map.class)))
                .thenReturn(Map.of("id", 2));

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("online", true));

        service.checkDeviceConnectivity("10.0.0.4", 80, "admin", "pass");

        HttpEntity<?> entity = captor.getValue();
        assertThat(entity.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("application/json"));
    }

    // -----------------------------------------------------------------------
    // listIsapiDeviceUsers
    // -----------------------------------------------------------------------

    @Test
    void listIsapiDeviceUsers_returnsUsers() {
        List<Map<String, Object>> users = List.of(
                Map.of("id", 10, "employeeNo", "E001", "name", "Alice"),
                Map.of("id", 11, "employeeNo", "E002", "name", "Bob")
        );
        when(restTemplate.exchange(
                eq("http://isapi:8081/api/devices/3/users"),
                eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(users));

        List<Map<String, Object>> result = service.listIsapiDeviceUsers(3L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("employeeNo", "E001");
    }

    @Test
    void listIsapiDeviceUsers_isapiError_returnsEmptyList() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("timeout"));

        List<Map<String, Object>> result = service.listIsapiDeviceUsers(3L);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // createIsapiDeviceUser
    // -----------------------------------------------------------------------

    @Test
    void createIsapiDeviceUser_sendsJsonAndReturnsCreatedUser() {
        Map<String, Object> body = Map.of("employeeNo", "E003", "name", "Carol");
        Map<String, Object> responseBody = Map.of("id", 20, "employeeNo", "E003", "name", "Carol");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices/5/users"), captor.capture(), eq(Map.class)))
                .thenReturn(responseBody);

        Map<String, Object> result = service.createIsapiDeviceUser(5L, body);

        assertThat(result).containsEntry("employeeNo", "E003");
        assertThat(captor.getValue().getHeaders().getContentType().toString())
                .contains("application/json");
    }

    // -----------------------------------------------------------------------
    // deleteIsapiDeviceUser
    // -----------------------------------------------------------------------

    @Test
    void deleteIsapiDeviceUser_callsDeleteEndpoint() {
        service.deleteIsapiDeviceUser(5L, 20L);

        verify(restTemplate).exchange(
                eq("http://isapi:8081/api/devices/5/users/20"),
                eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // -----------------------------------------------------------------------
    // getIsapiPunches
    // -----------------------------------------------------------------------

    @Test
    void getIsapiPunches_withAllParams_buildsCorrectUrl() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        service.getIsapiPunches(7L, "E001", 100);

        String url = urlCaptor.getValue();
        assertThat(url).contains("deviceId=7");
        assertThat(url).contains("employeeNo=E001");
        assertThat(url).contains("limit=100");
    }

    @Test
    void getIsapiPunches_withNoParams_doesNotAddQueryString() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(List.of()));

        service.getIsapiPunches(null, null, null);

        String url = urlCaptor.getValue();
        assertThat(url).doesNotContain("?");
    }

    // -----------------------------------------------------------------------
    // getIsapiFailedAttempts
    // -----------------------------------------------------------------------

    @Test
    void getIsapiFailedAttempts_returnsData() {
        List<Map<String, Object>> data = List.of(Map.of("id", 1, "identity", "unknown"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(data));

        List<Map<String, Object>> result = service.getIsapiFailedAttempts(null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("identity", "unknown");
    }

    // -----------------------------------------------------------------------
    // startIsapiDevice / stopIsapiDevice
    // -----------------------------------------------------------------------

    @Test
    void startIsapiDevice_callsStartEndpoint() {
        Map<String, Object> response = Map.of("id", 1, "running", true);
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices/1/start"), any(), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = service.startIsapiDevice(1L);

        assertThat(result).containsEntry("running", true);
    }

    @Test
    void stopIsapiDevice_callsStopEndpoint() {
        Map<String, Object> response = Map.of("id", 1, "running", false);
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices/1/stop"), any(), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = service.stopIsapiDevice(1L);

        assertThat(result).containsEntry("running", false);
    }

    @Test
    void resetIsapiDeviceCursor_callsResetEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("deviceId", 1);
        response.put("lastSerialNo", 0);
        response.put("lastEventTime", null);
        when(restTemplate.postForObject(
                eq("http://isapi:8081/api/devices/1/cursor/reset"), any(), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = service.resetIsapiDeviceCursor(1L);

        assertThat(result).containsEntry("lastSerialNo", 0);
        assertThat(result).containsEntry("lastEventTime", null);
    }

    // -----------------------------------------------------------------------
    // getIsapiDeviceStatus
    // -----------------------------------------------------------------------

    @Test
    void getIsapiDeviceStatus_returnsStatus() {
        when(restTemplate.getForObject(
                eq("http://isapi:8081/api/devices/2/status"), eq(Map.class)))
                .thenReturn(Map.of("online", true, "statusCode", 200));

        Map<String, Object> result = service.getIsapiDeviceStatus(2L);

        assertThat(result).containsEntry("online", true);
    }

    @Test
    void getIsapiDeviceStatus_isapiError_returnsNull() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = service.getIsapiDeviceStatus(2L);

        assertThat(result).isNull();
    }
}

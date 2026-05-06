package com.hic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client service that delegates device operations to the ISAPI microservice
 * via its REST API instead of calling device endpoints directly.
 *
 * <p>ISAPI base URL is configured via {@code isapi.base-url} in application.yml
 * (default: {@code http://localhost:8081}).
 *
 * <p>The {@code enabled} flag sent during device registration is controlled by
 * {@code isapi.device-enabled-default} (default: {@code false}).  Keeping it
 * {@code false} prevents ISAPI from starting the alert-stream immediately on
 * registration; use {@link #startDevice(String)} to activate a device explicitly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsapiClientService {

    private final RestTemplate restTemplate;

    @Value("${isapi.base-url:http://localhost:8081}")
    private String isapiBaseUrl;

    @Value("${isapi.device-enabled-default:false}")
    private boolean deviceEnabledDefault;

    // -----------------------------------------------------------------------
    // Device status check
    // -----------------------------------------------------------------------

    /**
     * Checks device connectivity by delegating to the ISAPI service.
     *
     * <p>The method first looks up the ISAPI-side device record for the given
     * {@code ip}. If no record exists yet it registers the device in ISAPI and
     * then queries its status. Returns {@code true} only when ISAPI reports the
     * device as online.
     *
     * @param ip       device IP address
     * @param port     device port (used only when creating a new ISAPI record)
     * @param username device username
     * @param password plain-text device password
     * @return {@code true} if the device is reachable/online according to ISAPI
     */
    public boolean checkDeviceConnectivity(String ip, int port, String username, String password) {
        try {
            Long isapiDeviceId = findIsapiDeviceIdByIp(ip);
            if (isapiDeviceId == null) {
                isapiDeviceId = registerDeviceInIsapi(ip, port, username, password);
            }
            if (isapiDeviceId == null) {
                log.warn("IsapiClientService: could not resolve ISAPI device id for ip={}", ip);
                return false;
            }
            return isDeviceOnline(isapiDeviceId);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: REST call failed while checking device ip={}: {}", ip, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("IsapiClientService: unexpected error checking device ip={}: {}", ip, e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Device lifecycle (start / stop)
    // -----------------------------------------------------------------------

    /**
     * Asks ISAPI to start the alert-stream worker for the device with the given IP.
     */
    public void startDevice(String ip) {
        try {
            Long id = findIsapiDeviceIdByIp(ip);
            if (id == null) {
                log.warn("IsapiClientService: cannot start – no ISAPI device found for ip={}", ip);
                return;
            }
            restTemplate.postForObject(isapiBaseUrl + "/api/devices/" + id + "/start", null, Object.class);
            log.info("IsapiClientService: started device id={} ip={}", id, ip);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to start device ip={}: {}", ip, e.getMessage());
        }
    }

    /**
     * Asks ISAPI to stop the alert-stream worker for the device with the given IP.
     */
    public void stopDevice(String ip) {
        try {
            Long id = findIsapiDeviceIdByIp(ip);
            if (id == null) {
                log.warn("IsapiClientService: cannot stop – no ISAPI device found for ip={}", ip);
                return;
            }
            restTemplate.postForObject(isapiBaseUrl + "/api/devices/" + id + "/stop", null, Object.class);
            log.info("IsapiClientService: stopped device id={} ip={}", id, ip);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to stop device ip={}: {}", ip, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the ISAPI-internal device ID for the given IP, or {@code null} if
     * no matching device exists in ISAPI.
     */
    private Long findIsapiDeviceIdByIp(String ip) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    isapiBaseUrl + "/api/devices",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return null;
            }

            return response.getBody().stream()
                    .filter(d -> ip.equals(d.get("ip")))
                    .map(d -> toLong(d.get("id")))
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to list ISAPI devices: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Registers a new device in ISAPI and returns its assigned ID.
     * Returns {@code null} if the registration fails.
     */
    private Long registerDeviceInIsapi(String ip, int port, String username, String password) {
        try {
            Map<String, Object> body = Map.of(
                    "ip", ip,
                    "port", port,
                    "username", username,
                    "password", password,
                    "enabled", deviceEnabledDefault);

            @SuppressWarnings("unchecked")
            Map<String, Object> created = restTemplate.postForObject(
                    isapiBaseUrl + "/api/devices", body, Map.class);

            if (created == null) {
                log.warn("IsapiClientService: ISAPI returned null body when registering ip={}", ip);
                return null;
            }
            Long id = toLong(created.get("id"));
            log.info("IsapiClientService: registered device in ISAPI id={} ip={}", id, ip);
            return id;
        } catch (RestClientException e) {
            log.warn("IsapiClientService: failed to register device ip={} in ISAPI: {}", ip, e.getMessage());
            return null;
        }
    }

    /**
     * Queries ISAPI for the online status of the device identified by {@code isapiDeviceId}.
     */
    private boolean isDeviceOnline(Long isapiDeviceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> status = restTemplate.getForObject(
                    isapiBaseUrl + "/api/devices/" + isapiDeviceId + "/status",
                    Map.class);

            if (status == null) {
                return false;
            }
            Object online = status.get("online");
            return Boolean.TRUE.equals(online);
        } catch (RestClientException e) {
            log.warn("IsapiClientService: status check failed for ISAPI device id={}: {}", isapiDeviceId, e.getMessage());
            return false;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

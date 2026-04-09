package com.hic.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HikvisionUtil {

    @Value("${hikvision.default-port:80}")
    private int defaultPort;

    @Value("${hikvision.default-username:admin}")
    private String defaultUsername;

    public String buildDeviceUrl(String deviceIp, int port, String path) {
        return String.format("http://%s:%d%s", deviceIp, port, path);
    }

    public String buildAttendanceSearchUrl(String deviceIp, int port) {
        return buildDeviceUrl(deviceIp, port, "/ISAPI/AccessControl/AcsEventCfg/capabilities");
    }

    public String buildEventSearchUrl(String deviceIp, int port) {
        return buildDeviceUrl(deviceIp, port, "/ISAPI/AccessControl/SearchEvent/capabilities");
    }

    public String formatDeviceRequest(String startTime, String endTime) {
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <AcsEventCond>
                    <searchID>1</searchID>
                    <searchResultPosition>0</searchResultPosition>
                    <maxResults>100</maxResults>
                    <startTime>%s</startTime>
                    <endTime>%s</endTime>
                </AcsEventCond>
                """, startTime, endTime);
    }

    public String parseISAPIResponse(String xmlResponse, String tagName) {
        if (xmlResponse == null || xmlResponse.isBlank()) return null;
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xmlResponse.indexOf(openTag);
        int end = xmlResponse.indexOf(closeTag);
        if (start >= 0 && end > start) {
            return xmlResponse.substring(start + openTag.length(), end).trim();
        }
        return null;
    }

    public boolean isSuccessResponse(String xmlResponse) {
        if (xmlResponse == null) return false;
        return xmlResponse.contains("<statusCode>200</statusCode>") ||
               xmlResponse.contains("<statusString>OK</statusString>");
    }
}

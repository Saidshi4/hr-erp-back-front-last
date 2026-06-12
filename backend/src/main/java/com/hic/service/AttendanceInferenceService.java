package com.hic.service;

import com.hic.model.AttendanceLog;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class AttendanceInferenceService {

    private static final Duration DUPLICATE_PUNCH_WINDOW = Duration.ofSeconds(60);

    public AttendanceInference inferDay(List<AttendanceLog> logs) {
        List<LocalDateTime> orderedPunches = normalizePunches(logs);
        if (orderedPunches.isEmpty()) {
            return new AttendanceInference(null, null, 0, false);
        }

        LocalDateTime firstEntry = orderedPunches.get(0);
        LocalDateTime lastExit = null;
        int workedMinutes = 0;
        boolean currentlyInside = false;

        for (int i = 0; i < orderedPunches.size(); i += 2) {
            LocalDateTime entry = orderedPunches.get(i);
            currentlyInside = true;

            if (i + 1 >= orderedPunches.size()) {
                break;
            }

            LocalDateTime exit = orderedPunches.get(i + 1);
            if (!exit.isAfter(entry)) {
                continue;
            }

            workedMinutes += (int) Duration.between(entry, exit).toMinutes();
            lastExit = exit;
            currentlyInside = false;
        }

        return new AttendanceInference(firstEntry, lastExit, workedMinutes, currentlyInside);
    }

    private List<LocalDateTime> normalizePunches(List<AttendanceLog> logs) {
        List<LocalDateTime> orderedPunches = logs.stream()
                .flatMap(log -> Stream.of(log.getCheckInTime(), log.getCheckOutTime()))
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        List<LocalDateTime> normalized = new ArrayList<>();
        for (LocalDateTime punch : orderedPunches) {
            if (normalized.isEmpty()) {
                normalized.add(punch);
                continue;
            }

            LocalDateTime previous = normalized.get(normalized.size() - 1);
            if (Duration.between(previous, punch).abs().compareTo(DUPLICATE_PUNCH_WINDOW) <= 0) {
                continue;
            }
            normalized.add(punch);
        }
        return normalized;
    }

    public record AttendanceInference(
            LocalDateTime firstEntry,
            LocalDateTime lastExit,
            int workedMinutes,
            boolean currentlyInside
    ) {
        public double workedHours() {
            return workedMinutes / 60.0;
        }
    }
}

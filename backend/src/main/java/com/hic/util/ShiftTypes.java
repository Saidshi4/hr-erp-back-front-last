package com.hic.util;

/**
 * Recognizes flexible / free-shift type codes used across timetable, employee, and UI.
 */
public final class ShiftTypes {

    private ShiftTypes() {
    }

    public static boolean isFlexible(String shiftType) {
        if (shiftType == null || shiftType.isBlank()) {
            return false;
        }
        String normalized = shiftType.trim().toUpperCase();
        return "FLEXIBLE".equals(normalized)
                || "FIRST_ENTRY".equals(normalized)
                || "SERBEST".equals(normalized);
    }
}

package com.hic.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftTypesTest {

    @Test
    void recognizesFlexibleAliases() {
        assertThat(ShiftTypes.isFlexible("FLEXIBLE")).isTrue();
        assertThat(ShiftTypes.isFlexible("flexible")).isTrue();
        assertThat(ShiftTypes.isFlexible("FIRST_ENTRY")).isTrue();
        assertThat(ShiftTypes.isFlexible("SERBEST")).isTrue();
        assertThat(ShiftTypes.isFlexible("STANDARD")).isFalse();
        assertThat(ShiftTypes.isFlexible("NIGHT")).isFalse();
        assertThat(ShiftTypes.isFlexible(null)).isFalse();
        assertThat(ShiftTypes.isFlexible("")).isFalse();
    }
}

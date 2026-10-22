package com.spendwise.transactionservice.lab;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL orders uuid values by their bytes, which is the same order as
 * their lowercase hex strings; these tests check the lab range in that order.
 */
class LabIdsTest {

    @Test
    void userAndCategoryIdsFallInsideTheLabRange() {
        String start = LabIds.RANGE_START.toString();
        String end = LabIds.RANGE_END.toString();

        assertThat(start).isEqualTo("1ab5eed0-0000-0000-0000-000000000000");
        assertThat(end).isEqualTo("1ab5eed0-ffff-ffff-ffff-ffffffffffff");
        for (UUID id : new UUID[]{LabIds.userId(1), LabIds.userId(20_000), LabIds.categoryId(0), LabIds.categoryId(14)}) {
            assertThat(id.toString()).isBetween(start, end);
            assertThat(LabIds.isLabId(id)).isTrue();
        }
    }

    @Test
    void userAndCategoryIdsNeverCollide() {
        assertThat(LabIds.userId(1)).isNotEqualTo(LabIds.categoryId(1));
        assertThat(LabIds.userId(LabIds.MAX_USERS).toString()).isLessThan(LabIds.categoryId(0).toString());
    }

    @Test
    void userIndexIsTheInverseOfUserId() {
        assertThat(LabIds.userIndex(LabIds.userId(1))).isEqualTo(1);
        assertThat(LabIds.userIndex(LabIds.userId(20_000))).isEqualTo(20_000);
        assertThatThrownBy(() -> LabIds.userIndex(LabIds.categoryId(3))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LabIds.userIndex(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomIdsAreNotLabIds() {
        assertThat(LabIds.isLabId(UUID.fromString("3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"))).isFalse();
        assertThat(LabIds.isLabId(null)).isFalse();
    }

    @Test
    void rejectsIndexesOutsideTheScheme() {
        assertThatThrownBy(() -> LabIds.userId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LabIds.categoryId(0x1_0000)).isInstanceOf(IllegalArgumentException.class);
    }
}

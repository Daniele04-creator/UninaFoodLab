package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseCardGridLayoutTest {

    @Test
    void twoVisibleCoursesFillTheAvailableRow() {
        assertThat(CourseCardGrid.columnCount(1092, 2)).isEqualTo(2);
        assertThat(CourseCardGrid.tileWidth(1092, 2)).isBetween(530.0, 540.0);
    }

    @Test
    void widerCatalogUsesThreeColumnsWhenEnoughCoursesAreVisible() {
        assertThat(CourseCardGrid.columnCount(1450, 5)).isEqualTo(3);
        assertThat(CourseCardGrid.tileWidth(1450, 5)).isBetween(465.0, 475.0);
    }

    @Test
    void fractionalViewportLeavesRoomForThreeSnappedTilesAt125PercentScale() {
        double availableWidth = 1185.6;
        double snappedTile = Math.ceil(CourseCardGrid.tileWidth(availableWidth, 4) * 1.25) / 1.25;
        double snappedGap = Math.ceil(20 * 1.25) / 1.25;

        assertThat(CourseCardGrid.columnCount(availableWidth, 4)).isEqualTo(3);
        assertThat(3 * snappedTile + 2 * snappedGap).isLessThanOrEqualTo(availableWidth - 2);
    }

    @Test
    void fractionalViewportLeavesRoomForTwoSnappedTilesAt125PercentScale() {
        double availableWidth = 1177.2;
        double snappedTile = Math.ceil(CourseCardGrid.tileWidth(availableWidth, 2) * 1.25) / 1.25;
        double snappedGap = Math.ceil(20 * 1.25) / 1.25;

        assertThat(CourseCardGrid.columnCount(availableWidth, 2)).isEqualTo(2);
        assertThat(2 * snappedTile + snappedGap).isLessThanOrEqualTo(availableWidth - 1);
    }

    @Test
    void narrowCatalogKeepsOneReadableColumn() {
        assertThat(CourseCardGrid.columnCount(580, 2)).isEqualTo(1);
        assertThat(CourseCardGrid.tileWidth(580, 2)).isEqualTo(580.0);
    }
}

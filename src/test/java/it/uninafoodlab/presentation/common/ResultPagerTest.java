package it.uninafoodlab.presentation.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResultPagerTest {
    @Test
    void pagesKeepEveryResultExactlyOnceIncludingTheLastPartialPage() {
        var results = IntStream.range(0, 25).boxed().toList();
        assertThat(ResultPager.count(results.size())).isEqualTo(3);
        assertThat(ResultPager.slice(results, 0)).containsExactlyElementsOf(results.subList(0, 12));
        assertThat(ResultPager.slice(results, 1)).containsExactlyElementsOf(results.subList(12, 24));
        assertThat(ResultPager.slice(results, 2)).containsExactly(24);
    }

    @Test
    void removedResultsAndEmptyFiltersCannotLeaveThePageOutOfRange() {
        assertThat(ResultPager.clamp(7, 13)).isEqualTo(1);
        assertThat(ResultPager.clamp(-1, 13)).isZero();
        assertThat(ResultPager.clamp(7, 0)).isZero();
        assertThat(ResultPager.count(0)).isEqualTo(1);
        assertThat(ResultPager.slice(java.util.List.of(), 7)).isEmpty();
    }
}

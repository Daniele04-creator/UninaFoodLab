package it.uninafoodlab.application.common;

import java.util.List;

public record Page<T>(List<T> items, long totalCount) {

    public Page {
        items = List.copyOf(items);
        if (totalCount < items.size()) {
            throw new IllegalArgumentException("Total count cannot be smaller than page size");
        }
    }
}

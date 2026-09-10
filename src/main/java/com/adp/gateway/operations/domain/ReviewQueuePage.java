package com.adp.gateway.operations.domain;

import java.util.List;

public record ReviewQueuePage(List<ReviewQueueItem> items, int page, int size, long totalElements) {
    public ReviewQueuePage {
        items = List.copyOf(items);
    }
}

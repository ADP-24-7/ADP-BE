package com.adp.gateway.operations.domain;

import java.util.List;

public record SecurityFindingPage(List<SecurityFindingItem> items, int page, int size, long totalElements) {
    public SecurityFindingPage {
        items = List.copyOf(items);
    }
}

package com.adp.gateway.operations.domain;

import java.util.List;

public record AdminIdentityPage(List<AdminIdentityItem> items, int page, int size, long totalElements) {
    public AdminIdentityPage {
        items = List.copyOf(items);
    }
}

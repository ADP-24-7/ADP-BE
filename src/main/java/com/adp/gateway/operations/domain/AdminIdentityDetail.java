package com.adp.gateway.operations.domain;

import java.util.List;

public record AdminIdentityDetail(
    AdminIdentityItem identity,
    List<AdminIdentityPermission> permissions
) {
    public AdminIdentityDetail {
        permissions = List.copyOf(permissions);
    }
}

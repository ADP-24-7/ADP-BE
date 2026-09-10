insert into auth_user_credential (
    principal_id,
    password_hash,
    password_changed_at,
    failed_attempts,
    locked_until,
    updated_at
) values
    ('auditor-local', '$2y$12$Wb/A5aJaOuSTmajJyyob8OGCdeNdEk0awNHdw8fWEdZD588/ZXH1S', now(), 0, null, now()),
    ('privileged-operator-local', '$2y$12$jxnznz4pXAThL6vk6.7wWeG0f0MxbHWmtbOTVAbVJB8Kif6.YvKZe', now(), 0, null, now()),
    ('operator-local', '$2y$12$sXKQ5wjQsrzI9rELyG43GOHmvYRH1BMZNuNUrCtU976E1DXXGSupK', now(), 0, null, now())
on conflict (principal_id) do update set
    password_hash = excluded.password_hash,
    failed_attempts = 0,
    locked_until = null,
    updated_at = now();

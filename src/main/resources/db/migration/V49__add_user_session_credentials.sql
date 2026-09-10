create table auth_user_credential (
    principal_id varchar(80) primary key references auth_principal (principal_id),
    password_hash varchar(100) not null,
    password_changed_at timestamptz not null default now(),
    failed_attempts integer not null default 0,
    locked_until timestamptz,
    updated_at timestamptz not null default now(),
    constraint ck_auth_user_credential_password_hash
        check (password_hash ~ '^\$2[aby]\$[0-9]{2}\$.{53}$'),
    constraint ck_auth_user_credential_failed_attempts
        check (failed_attempts >= 0)
);

create index idx_auth_user_credential_locked_until
    on auth_user_credential (locked_until)
    where locked_until is not null;

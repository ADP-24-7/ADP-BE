create table auth_principal (
    principal_id varchar(80) primary key,
    principal_type varchar(40) not null,
    display_name varchar(160) not null,
    workload_scope varchar(120) not null,
    subject_authorization_required boolean not null,
    enabled boolean not null,
    created_at timestamptz not null default now()
);

create table auth_principal_role (
    principal_id varchar(80) not null references auth_principal (principal_id),
    role_name varchar(80) not null,
    primary key (principal_id, role_name)
);

create table auth_api_key (
    key_id varchar(80) primary key,
    principal_id varchar(80) not null references auth_principal (principal_id),
    key_hash varchar(64) not null unique,
    enabled boolean not null,
    created_at timestamptz not null default now()
);

insert into auth_principal (
    principal_id,
    principal_type,
    display_name,
    workload_scope,
    subject_authorization_required,
    enabled
) values (
    'svc_local_runtime',
    'SERVICE',
    'Local Runtime Harness',
    '*',
    false,
    true
);

insert into auth_principal_role (principal_id, role_name) values
    ('svc_local_runtime', 'RUNTIME_EXECUTOR'),
    ('svc_local_runtime', 'OPERATOR');

insert into auth_api_key (
    key_id,
    principal_id,
    key_hash,
    enabled
) values (
    'key_local_runtime',
    'svc_local_runtime',
    '2bcd99491790f5324dd084241b713b576a92b12c497f3b553230d49cc72e15c2',
    true
);

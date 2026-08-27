create table auth_principal (
    principal_id varchar(80) primary key,
    principal_type varchar(40) not null,
    display_name varchar(160) not null,
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

create table auth_principal_workload (
    principal_id varchar(80) not null references auth_principal (principal_id),
    workload_id varchar(120) not null,
    primary key (principal_id, workload_id)
);

create table auth_subject_grant (
    principal_id varchar(80) not null references auth_principal (principal_id),
    workload_id varchar(120) not null,
    action_name varchar(80) not null,
    purpose varchar(160) not null,
    subject_type varchar(80) not null,
    subject_id varchar(160) not null,
    primary key (principal_id, workload_id, action_name, purpose, subject_type, subject_id)
);

alter table auth_principal
    add column institution_id varchar(120);

alter table runtime.runtime_execution
    add column authorization_status varchar(40);

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_authorization_status
    check (authorization_status is null or authorization_status in ('PASSED', 'DENIED'));

create index idx_auth_principal_institution
    on auth_principal (institution_id);

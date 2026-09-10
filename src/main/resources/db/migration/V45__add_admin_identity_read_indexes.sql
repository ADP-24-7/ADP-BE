create index idx_auth_principal_operations_scope
    on auth_principal (institution_id, display_name, principal_id);

create index idx_auth_principal_role_operations_search
    on auth_principal_role (role_name, principal_id);

create index idx_auth_principal_workload_operations_search
    on auth_principal_workload (workload_id, principal_id);

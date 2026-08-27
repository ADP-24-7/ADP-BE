insert into auth_principal (
    principal_id,
    principal_type,
    display_name,
    subject_authorization_required,
    enabled
) values (
    'svc_local_runtime',
    'SERVICE',
    'Local Runtime Harness',
    true,
    true
) on conflict (principal_id) do nothing;

insert into auth_principal_role (principal_id, role_name) values
    ('svc_local_runtime', 'RUNTIME_EXECUTOR'),
    ('svc_local_runtime', 'OPERATOR')
on conflict (principal_id, role_name) do nothing;

insert into auth_principal_workload (principal_id, workload_id) values
    ('svc_local_runtime', 'workload_be0'),
    ('svc_local_runtime', 'workload_local'),
    ('svc_local_runtime', 'customer_summary')
on conflict (principal_id, workload_id) do nothing;

insert into auth_subject_grant (
    principal_id,
    workload_id,
    action_name,
    purpose,
    subject_type,
    subject_id
) values
    ('svc_local_runtime', 'workload_be0', 'RUNTIME_EXECUTE', 'BE-0 local E2E', 'customer', 'mock-subject'),
    ('svc_local_runtime', 'workload_local', 'RUNTIME_EXECUTE', 'local-smoke', 'customer', 'mock-subject'),
    ('svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE', 'CUSTOMER_SUPPORT', 'customer', 'customer-100')
on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing;

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
) on conflict (key_id) do nothing;

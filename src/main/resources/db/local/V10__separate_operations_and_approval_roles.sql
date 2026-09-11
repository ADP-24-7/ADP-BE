-- Local demo accounts must demonstrate the production role boundary.
delete from auth_principal_role
where principal_id = 'operator-local'
  and role_name = 'PRIVILEGED_OPERATOR';

alter table runtime.response_sensitive_finding
    add column source_data_class varchar(80),
    add column transform_strategy varchar(80),
    add column field_treatment varchar(80),
    add column outbound_field_path_digest varchar(64);

alter table runtime.response_sensitive_finding
    add constraint chk_response_finding_source_data_class
        check (source_data_class is null or source_data_class in (
            'CUSTOMER_IDENTIFIER', 'ACCOUNT_IDENTIFIER', 'TRANSACTION_IDENTIFIER',
            'FINANCIAL_AMOUNT', 'FINANCIAL_METADATA', 'BUSINESS_METADATA', 'UNKNOWN'
        )),
    add constraint chk_response_finding_transform_strategy
        check (transform_strategy is null or transform_strategy in (
            'MASK', 'HMAC_PSEUDO', 'VAULT_TOKEN', 'REMOVE', 'KEEP', 'GENERALIZE', 'FIELD_SEPARATION'
        )),
    add constraint chk_response_finding_field_treatment
        check (field_treatment is null or field_treatment in (
            'REMOVED', 'TRANSFORMED', 'KEEP_EXACT_PROTECTED'
        )),
    add constraint chk_response_finding_field_path_digest
        check (outbound_field_path_digest is null or outbound_field_path_digest ~ '^[0-9a-f]{64}$'),
    add constraint chk_response_finding_reflection_metadata_shape
        check (
            (
                finding_type = 'RAW_VALUE_REFLECTION'
                and (
                    (
                        source_data_class is null
                        and transform_strategy is null
                        and field_treatment is null
                        and outbound_field_path_digest is null
                    )
                    or (
                        source_data_class is not null
                        and transform_strategy is not null
                        and field_treatment is not null
                        and outbound_field_path_digest is not null
                    )
                )
            )
            or (
                finding_type <> 'RAW_VALUE_REFLECTION'
                and source_data_class is null
                and transform_strategy is null
                and field_treatment is null
                and outbound_field_path_digest is null
            )
        );

create index idx_response_sensitive_finding_calibration
    on runtime.response_sensitive_finding (
        execution_id, finding_type, source_data_class, transform_strategy, field_treatment
    );

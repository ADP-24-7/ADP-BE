alter table runtime.digital_asset_transaction
    add constraint chk_digital_asset_state_reconciliation_combination check (
        (settlement_status in ('REQUESTED', 'POLICY_APPROVED', 'READY_TO_SUBMIT', 'SUBMITTED', 'SETTLING', 'SENT_UNKNOWN')
            and reconciliation_result = 'WAIT')
        or (settlement_status = 'SETTLED'
            and reconciliation_result in ('MATCH', 'RECOVERED', 'MISMATCH', 'CRITICAL_MISMATCH'))
        or (settlement_status in ('FAILED', 'RECONCILIATION_REQUIRED')
            and reconciliation_result in ('WAIT', 'MISMATCH', 'CRITICAL_MISMATCH'))
    );

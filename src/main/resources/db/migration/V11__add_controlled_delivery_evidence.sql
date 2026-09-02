alter table runtime.runtime_execution
    add column controlled_delivery_status varchar(40),
    add column controlled_delivery_response_digest varchar(200),
    add column controlled_delivery_reason_code varchar(160),
    add column controlled_delivered_at timestamptz;

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_controlled_delivery_status
    check (controlled_delivery_status is null or controlled_delivery_status in ('DELIVERED', 'WITHHELD'));

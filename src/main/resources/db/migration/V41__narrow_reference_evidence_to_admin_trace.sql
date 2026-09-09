alter table evidence.reference_evidence
    add column analysis_ref varchar(500),
    add column analysis_locator varchar(500);

update evidence.reference_evidence
set analysis_ref = source_ref,
    analysis_locator = source_locator
where analysis_ref is null or analysis_locator is null;

alter table evidence.reference_evidence
    alter column analysis_ref set not null,
    alter column analysis_locator set not null;

update evidence.reference_evidence
set status = 'REFERENCE_ONLY'
where status <> 'REFERENCE_ONLY';

alter table evidence.reference_evidence
    drop constraint reference_evidence_status_check,
    add constraint chk_reference_evidence_reference_only
        check (status = 'REFERENCE_ONLY');

drop table evidence.reference_evidence_policy_artifact;

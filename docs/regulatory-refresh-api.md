# Regulatory refresh and evidence lineage API

## Manual refresh

`POST /api/admin/regulatory-refresh` accepts `{"sourceIds":["SOURCE_ID"]}`. An empty or null list refreshes the complete canonical registry. Only a `PRIVILEGED_OPERATOR` may invoke it.

The endpoint delegates to the same service used by the scheduler. The service calls ADP-DA at `POST /internal/regulatory/refresh` and rejects any response that requests automatic activation. Fetch/parse failures and pending candidates therefore cannot mutate current policy selection or runtime decisions.

Configuration:

- `REGULATORY_REFRESH_ENABLED` (default `false`)
- `REGULATORY_REFRESH_CRON` (default `0 0 3 * * *`)
- `ADP_REGULATORY_REFRESH_BASE_URL` (default `http://localhost:8010`)
- `ADP_REGULATORY_REFRESH_TOKEN` (required shared internal-service credential)

## Evidence binding and trace

- `POST /api/admin/reference-evidence/{evidenceId}/versions/{evidenceVersion}/policy-bindings`
- `GET /api/admin/reference-evidence/policy-artifacts/{artifactId}/versions/{artifactVersion}`

The V54/V55 binding preserves the DA `regulatory_evidence_id` as BE `evidence_id`, the official `source_digest`, explicit `requirementRefs` and `controlRefs`, policy version, lifecycle state, execution pack, workload, and purpose. A successfully materialized association is `CONNECTED` independently of its separately reported lifecycle stage.

The binding request requires non-empty Requirement and Control identity lists. Binding is trace metadata only: it cannot transition, approve, activate, overwrite Runtime policy, or bypass maker-checker controls. DRAFT materializations therefore remain DRAFT.

package com.adp.gateway.runtime.api;

final class RuntimeOpenApiExamples {
    static final String DIGITAL_ASSET_EXECUTION = """
        {
          "institutionId": "institution_local",
          "approvalReference": "approval_digital_asset_purchase_v1",
          "workloadId": "tokenized_asset_purchase",
          "purposeCode": "DIGITAL_ASSET_PURCHASE",
          "subjectScope": "customer:customer-100",
          "destinationProfileId": "dest_mock_asset_platform_v1",
          "idempotencyKey": "replace-with-a-unique-key",
          "processingContexts": ["DIGITAL_ASSET"],
          "input": {
            "approvedTransactionReference": "approved-tx-local-001",
            "customerId": "customer-100",
            "accountId": "acct-100-1",
            "outboundRequest": {
              "requestedAsset": {
                "chainId": "eip155:1",
                "assetKind": "FUNGIBLE_TOKEN",
                "assetSymbol": "asset-krw-token-001",
                "assetContractAddress": "0x0000000000000000000000000000000000000001",
                "operation": "TRANSFER",
                "tokenId": null
              },
              "requestedAmount": "10000",
              "requestedDestination": "wallet-test-001",
              "requestedBeneficiaryReference": "beneficiary-local-001",
              "regulatoryOutboundData": {}
            }
          }
        }
        """;

    private RuntimeOpenApiExamples() {
    }
}

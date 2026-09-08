delete from retrieval_profile_field
where profile_id = 'profile_tokenized_asset_purchase'
  and dataset_name = 'request'
  and field_name in ('walletAddress', 'assetId', 'amount');

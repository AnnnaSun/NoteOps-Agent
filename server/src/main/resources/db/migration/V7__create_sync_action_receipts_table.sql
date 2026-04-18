create table if not exists sync_action_receipts (
    id uuid primary key,
    user_id uuid not null,
    client_id varchar(128) not null,
    offline_action_id varchar(128) not null,
    action_type varchar(64) not null,
    entity_type varchar(64),
    entity_id uuid,
    trace_id uuid,
    status varchar(32) not null,
    error_code varchar(64),
    error_message text,
    payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz,
    processed_at timestamptz not null default current_timestamp,
    created_at timestamptz not null default current_timestamp,
    constraint fk_sync_action_receipts_trace_id foreign key (trace_id) references agent_traces (id),
    constraint uq_sync_action_receipts_user_client_action unique (user_id, client_id, offline_action_id)
);

create index if not exists idx_sync_action_receipts_user_id_processed_at_desc
    on sync_action_receipts (user_id, processed_at desc);

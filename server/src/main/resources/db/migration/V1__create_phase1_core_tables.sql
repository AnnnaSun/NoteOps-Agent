create table if not exists notes (
    id uuid primary key,
    user_id uuid not null,
    note_type varchar(64) not null,
    status varchar(32) not null,
    title varchar(255) not null,
    current_summary text,
    current_key_points jsonb not null default '[]'::jsonb,
    current_tags jsonb not null default '[]'::jsonb,
    current_topic_labels jsonb not null default '[]'::jsonb,
    current_relation_summary jsonb not null default '{}'::jsonb,
    importance_score numeric(5, 2) not null default 0,
    latest_content_id uuid,
    version integer not null default 1,
    extra_attributes jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_notes_importance_score_range check (importance_score >= 0 and importance_score <= 100),
    constraint chk_notes_version_positive check (version >= 1)
);

create table if not exists note_contents (
    id uuid primary key,
    user_id uuid not null,
    note_id uuid not null,
    content_type varchar(32) not null,
    source_uri text,
    canonical_uri text,
    source_snapshot jsonb not null default '{}'::jsonb,
    raw_text text,
    clean_text text,
    analysis_result jsonb not null default '{}'::jsonb,
    is_current_view_source boolean not null default false,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_note_contents_note_id foreign key (note_id) references notes (id),
    constraint chk_note_contents_content_type check (
        content_type in ('PRIMARY', 'UPDATE', 'EVIDENCE', 'TRANSCRIPT', 'CAPTURE_RAW')
    )
);

alter table notes
    add constraint fk_notes_latest_content_id
        foreign key (latest_content_id) references note_contents (id);

create table if not exists review_states (
    id uuid primary key,
    user_id uuid not null,
    note_id uuid not null,
    queue_type varchar(16) not null,
    mastery_score numeric(5, 2) not null default 0,
    last_reviewed_at timestamptz,
    next_review_at timestamptz,
    completion_status varchar(16) not null default 'NOT_STARTED',
    completion_reason varchar(16),
    unfinished_count integer not null default 0,
    retry_after_hours integer not null default 0,
    review_meta jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_review_states_note_id foreign key (note_id) references notes (id),
    constraint uq_review_states_user_note_queue unique (user_id, note_id, queue_type),
    constraint chk_review_states_queue_type check (queue_type in ('SCHEDULE', 'RECALL')),
    constraint chk_review_states_completion_status check (
        completion_status in ('COMPLETED', 'PARTIAL', 'NOT_STARTED', 'ABANDONED')
    ),
    constraint chk_review_states_completion_reason check (
        completion_reason is null or completion_reason in ('TIME_LIMIT', 'TOO_HARD', 'VAGUE_MEMORY', 'DEFERRED')
    ),
    constraint chk_review_states_mastery_score_range check (mastery_score >= 0 and mastery_score <= 100),
    constraint chk_review_states_unfinished_count_non_negative check (unfinished_count >= 0),
    constraint chk_review_states_retry_after_hours_non_negative check (retry_after_hours >= 0)
);

create table if not exists agent_traces (
    id uuid primary key,
    user_id uuid not null,
    entry_type varchar(64) not null,
    goal text,
    root_entity_type varchar(64),
    root_entity_id uuid,
    status varchar(32) not null,
    orchestrator_state jsonb not null default '{}'::jsonb,
    worker_sequence jsonb not null default '[]'::jsonb,
    result_summary text,
    started_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz not null default current_timestamp
);

create table if not exists tasks (
    id uuid primary key,
    user_id uuid not null,
    note_id uuid,
    task_source varchar(16) not null,
    task_type varchar(64) not null,
    title varchar(255) not null,
    description text,
    status varchar(16) not null,
    priority integer not null default 0,
    due_at timestamptz,
    related_entity_type varchar(64),
    related_entity_id uuid,
    extra_attributes jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_tasks_note_id foreign key (note_id) references notes (id),
    constraint chk_tasks_task_source check (task_source in ('SYSTEM', 'USER')),
    constraint chk_tasks_status check (status in ('TODO', 'IN_PROGRESS', 'DONE', 'SKIPPED', 'CANCELLED'))
);

create table if not exists change_proposals (
    id uuid primary key,
    user_id uuid not null,
    note_id uuid not null,
    trace_id uuid,
    proposal_type varchar(64) not null,
    target_layer varchar(16) not null,
    risk_level varchar(8) not null,
    diff_summary text not null,
    before_snapshot jsonb not null default '{}'::jsonb,
    after_snapshot jsonb not null default '{}'::jsonb,
    source_refs jsonb not null default '[]'::jsonb,
    rollback_token varchar(128),
    status varchar(16) not null default 'PENDING_REVIEW',
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_change_proposals_note_id foreign key (note_id) references notes (id),
    constraint fk_change_proposals_trace_id foreign key (trace_id) references agent_traces (id),
    constraint chk_change_proposals_target_layer check (
        target_layer in ('INTERPRETATION', 'METADATA', 'RELATION')
    ),
    constraint chk_change_proposals_risk_level check (risk_level in ('LOW', 'MEDIUM', 'HIGH')),
    constraint chk_change_proposals_status check (
        status in ('PENDING_REVIEW', 'APPLIED', 'REJECTED', 'ROLLED_BACK')
    )
);

create table if not exists capture_jobs (
    id uuid primary key,
    user_id uuid not null,
    input_type varchar(8) not null,
    source_uri text,
    raw_input text,
    status varchar(32) not null,
    extracted_payload jsonb not null default '{}'::jsonb,
    analysis_result jsonb not null default '{}'::jsonb,
    consolidation_result jsonb not null default '{}'::jsonb,
    error_code varchar(64),
    error_message text,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_capture_jobs_input_type check (input_type in ('TEXT', 'URL'))
);

create table if not exists tool_invocation_logs (
    id uuid primary key,
    user_id uuid not null,
    trace_id uuid not null,
    tool_name varchar(128) not null,
    status varchar(32) not null,
    input_digest jsonb not null default '{}'::jsonb,
    output_digest jsonb not null default '{}'::jsonb,
    latency_ms integer,
    error_code varchar(64),
    error_message text,
    created_at timestamptz not null default current_timestamp,
    constraint fk_tool_invocation_logs_trace_id foreign key (trace_id) references agent_traces (id),
    constraint chk_tool_invocation_logs_latency_ms_non_negative check (latency_ms is null or latency_ms >= 0)
);

create table if not exists user_action_events (
    id uuid primary key,
    user_id uuid not null,
    event_type varchar(64) not null,
    entity_type varchar(64),
    entity_id uuid,
    session_id varchar(128),
    trace_id uuid,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint fk_user_action_events_trace_id foreign key (trace_id) references agent_traces (id)
);

create index if not exists idx_notes_user_id_updated_at_desc
    on notes (user_id, updated_at desc);

create index if not exists idx_note_contents_note_id_created_at_desc
    on note_contents (note_id, created_at desc);

create index if not exists idx_review_states_user_id_next_review_at
    on review_states (user_id, next_review_at);

create index if not exists idx_tasks_user_id_status_due_at
    on tasks (user_id, status, due_at);

create index if not exists idx_change_proposals_user_id_status_risk_level
    on change_proposals (user_id, status, risk_level);

create index if not exists idx_capture_jobs_user_id_status_created_at
    on capture_jobs (user_id, status, created_at);

create index if not exists idx_agent_traces_user_id_created_at_desc
    on agent_traces (user_id, created_at desc);

create index if not exists idx_user_action_events_user_id_created_at_desc
    on user_action_events (user_id, created_at desc);

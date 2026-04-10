create table trend_items (
    id uuid primary key,
    user_id uuid not null,
    source_type varchar(32) not null,
    source_item_key varchar(255) not null,
    title varchar(255) not null,
    url text not null,
    summary text,
    normalized_score numeric(5, 2) not null default 0,
    analysis_payload jsonb not null default '{}'::jsonb,
    extra_attributes jsonb not null default '{}'::jsonb,
    status varchar(32) not null,
    suggested_action varchar(32),
    source_published_at timestamptz,
    last_ingested_at timestamptz,
    converted_note_id uuid,
    converted_idea_id uuid,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_trend_items_converted_note_id foreign key (converted_note_id) references notes (id),
    constraint fk_trend_items_converted_idea_id foreign key (converted_idea_id) references ideas (id),
    constraint chk_trend_items_source_type check (source_type in ('HN', 'GITHUB')),
    constraint chk_trend_items_status check (
        status in ('INGESTED', 'ANALYZED', 'IGNORED', 'SAVED_AS_NOTE', 'PROMOTED_TO_IDEA')
    ),
    constraint chk_trend_items_suggested_action check (
        suggested_action is null or suggested_action in ('IGNORE', 'SAVE_AS_NOTE', 'PROMOTE_TO_IDEA')
    ),
    constraint chk_trend_items_normalized_score_range check (
        normalized_score >= 0 and normalized_score <= 100
    )
);

create unique index uq_trend_items_user_source_item
    on trend_items (user_id, source_type, source_item_key);

create index idx_trend_items_user_status_updated_at_desc
    on trend_items (user_id, status, updated_at desc);

create index idx_trend_items_user_score_updated_at_desc
    on trend_items (user_id, normalized_score desc, updated_at desc);

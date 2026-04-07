create table if not exists ideas (
    id uuid primary key,
    user_id uuid not null,
    source_mode varchar(32) not null,
    source_note_id uuid,
    title varchar(255) not null,
    raw_description text,
    status varchar(32) not null,
    assessment_result jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint fk_ideas_source_note_id foreign key (source_note_id) references notes (id),
    constraint chk_ideas_source_mode check (source_mode in ('FROM_NOTE', 'INDEPENDENT')),
    constraint chk_ideas_status check (status in ('CAPTURED', 'ASSESSED', 'PLANNED', 'IN_PROGRESS', 'ARCHIVED')),
    constraint chk_ideas_source_binding check (
        (source_mode = 'FROM_NOTE' and source_note_id is not null)
        or (source_mode = 'INDEPENDENT' and source_note_id is null)
    )
);

create index if not exists idx_ideas_user_id_updated_at_desc
    on ideas (user_id, updated_at desc);

create index if not exists idx_ideas_user_id_status_updated_at_desc
    on ideas (user_id, status, updated_at desc);

create index if not exists idx_ideas_user_id_source_note_id_updated_at_desc
    on ideas (user_id, source_note_id, updated_at desc);

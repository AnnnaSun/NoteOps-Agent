create table if not exists user_preference_profiles (
    id uuid primary key,
    user_id uuid not null,
    interest_profile jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uq_user_preference_profiles_user_id unique (user_id)
);

create index if not exists idx_user_preference_profiles_user_id_updated_at_desc
    on user_preference_profiles (user_id, updated_at desc);

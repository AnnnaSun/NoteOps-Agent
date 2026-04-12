alter table ideas
    add column if not exists source_trend_item_id uuid;

alter table ideas drop constraint if exists fk_ideas_source_trend_item_id;

alter table ideas
    add constraint fk_ideas_source_trend_item_id foreign key (source_trend_item_id) references trend_items (id);

alter table ideas drop constraint if exists chk_ideas_source_binding;
alter table ideas drop constraint if exists chk_ideas_source_mode;

alter table ideas
    add constraint chk_ideas_source_mode check (source_mode in ('FROM_NOTE', 'MANUAL', 'FROM_TREND'));

alter table ideas
    add constraint chk_ideas_source_binding check (
        (source_mode = 'FROM_NOTE' and source_note_id is not null and source_trend_item_id is null)
        or (source_mode = 'FROM_TREND' and source_trend_item_id is not null and source_note_id is null)
        or (source_mode = 'MANUAL' and source_note_id is null and source_trend_item_id is null)
    );

create index if not exists idx_ideas_user_id_source_trend_item_id_updated_at_desc
    on ideas (user_id, source_trend_item_id, updated_at desc)
    where source_trend_item_id is not null;

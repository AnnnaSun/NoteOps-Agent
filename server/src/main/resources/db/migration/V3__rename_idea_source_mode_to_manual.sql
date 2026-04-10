update ideas
set source_mode = 'MANUAL'
where source_mode = 'INDEPENDENT';

alter table ideas drop constraint if exists chk_ideas_source_binding;
alter table ideas drop constraint if exists chk_ideas_source_mode;

alter table ideas
    add constraint chk_ideas_source_mode check (source_mode in ('FROM_NOTE', 'MANUAL'));

alter table ideas
    add constraint chk_ideas_source_binding check (
        (source_mode = 'FROM_NOTE' and source_note_id is not null)
        or (source_mode = 'MANUAL' and source_note_id is null)
    );

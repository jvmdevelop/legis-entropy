-- User-uploaded templates + richer slot schema.
-- Existing seeded system templates keep is_system=true and user_id=null so
-- they remain available to everyone.
alter table document_templates add column if not exists user_id varchar(255);
alter table document_templates add column if not exists source_format varchar(32) not null default 'MANUAL';
alter table document_templates add column if not exists source_object_name varchar(1024);
alter table document_templates add column if not exists slots_json text;
alter table document_templates add column if not exists ai_summary text;

-- Lookup paths used by the global Templates page.
create index if not exists idx_document_templates_user on document_templates (user_id);
create index if not exists idx_document_templates_system on document_templates (is_system);

-- Mirror the legacy placeholders_json into the new slots_json for any seed row
-- that doesn't yet have it. Slots are richer (label + context + placement +
-- type + aiHint), but for the seeded examples a minimal envelope is enough
-- until the operator re-edits them.
update document_templates
set slots_json = placeholders_json
where slots_json is null and placeholders_json is not null;

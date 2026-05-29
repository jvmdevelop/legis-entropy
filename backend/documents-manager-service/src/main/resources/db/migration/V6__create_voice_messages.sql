-- Voice messages (audio uploads with transcription + violation analysis).
-- Audio binaries live in MinIO under {object_name}; the transcript and
-- structured analysis are kept here for fast retrieval.
create table if not exists voice_messages (
    id varchar(64) primary key,
    user_id varchar(255) not null,
    graph_id varchar(64),
    situation_id varchar(64),
    conversation_id varchar(64),
    file_name varchar(255),
    content_type varchar(128),
    object_name varchar(1024) not null,
    duration_ms integer,
    language varchar(16),
    status varchar(32) not null,
    transcript text,
    -- JSON array of {speaker, startMs, endMs, text} segments.
    transcript_json text,
    -- JSON {classification, severity, threats[], articles[], summary}
    analysis_json text,
    error_message text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_voice_messages_user on voice_messages (user_id);
create index if not exists idx_voice_messages_graph on voice_messages (graph_id);
create index if not exists idx_voice_messages_situation on voice_messages (situation_id);
create index if not exists idx_voice_messages_status on voice_messages (status);

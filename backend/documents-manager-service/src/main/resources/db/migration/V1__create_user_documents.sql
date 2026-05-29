create table if not exists user_documents (
    id varchar(255) primary key,
    user_id varchar(255) not null,
    file_name varchar(255) not null,
    content_type varchar(255),
    object_name varchar(1024) not null,
    status varchar(32) not null,
    chunk_count integer,
    error_message text,
    created_at timestamp not null,
    updated_at timestamp
);

create index if not exists idx_user_documents_user_id on user_documents (user_id);
create index if not exists idx_user_documents_status on user_documents (status);
create index if not exists idx_user_documents_user_status on user_documents (user_id, status);

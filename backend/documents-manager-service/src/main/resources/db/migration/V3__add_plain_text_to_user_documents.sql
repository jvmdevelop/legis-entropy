alter table user_documents
    add column if not exists plain_text text;

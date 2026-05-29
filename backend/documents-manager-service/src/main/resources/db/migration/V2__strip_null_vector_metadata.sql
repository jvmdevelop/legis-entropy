do $$
begin
    if to_regclass('public.vector_store') is not null then
        update vector_store
        set metadata = jsonb_strip_nulls(metadata::jsonb)
        where metadata is not null;
    end if;
end $$;

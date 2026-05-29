-- Document templates used to generate legal documents (claims, complaints,
-- contracts) from a Situation + linked evidence. The template body is
-- Markdown with {{placeholder}} tokens; the placeholder list lives in
-- placeholders_json so the UI can render a structured form.
create table if not exists document_templates (
    id varchar(64) primary key,
    code varchar(64) unique not null,
    title varchar(255) not null,
    description text,
    kind varchar(64) not null,
    body text not null,
    placeholders_json text,
    suggested_law_codes text,
    is_system boolean not null default false,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_document_templates_kind on document_templates (kind);

-- Documents produced by rendering a template. The rendered Markdown lives in
-- `body_markdown` and is mirrored to a MinIO object for downloads.
create table if not exists generated_documents (
    id varchar(64) primary key,
    user_id varchar(255) not null,
    template_id varchar(64) references document_templates(id) on delete set null,
    template_code varchar(64),
    graph_id varchar(64),
    situation_id varchar(64),
    title varchar(255) not null,
    body_markdown text not null,
    object_name varchar(1024),
    variables_json text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_generated_documents_user on generated_documents (user_id);
create index if not exists idx_generated_documents_situation on generated_documents (situation_id);
create index if not exists idx_generated_documents_graph on generated_documents (graph_id);

-- Seed a handful of base templates. Codes are stable; UI keys off them.
insert into document_templates (id, code, title, description, kind, body, placeholders_json, suggested_law_codes, is_system, created_at, updated_at) values
('tpl-police-threat', 'POLICE_THREAT_CLAIM',
 'Заявление в полицию об угрозе',
 'Заявление о привлечении к ответственности по ст. 115 УК РК (Угроза).',
 'CLAIM',
 e'**Начальнику {{police_unit}}**\n\nот {{applicant_full_name}},\nпроживающего по адресу: {{applicant_address}},\nтел.: {{applicant_phone}}\n\n# ЗАЯВЛЕНИЕ\n\nПрошу провести проверку и привлечь к уголовной ответственности гражданина {{suspect_full_name}} по ст. 115 УК РК (Угроза).\n\n{{incident_date}} г. в {{incident_time}}, по адресу {{incident_place}}, гражданин {{suspect_full_name}} в мой адрес высказал угрозы следующего содержания:\n\n> {{threat_quote}}\n\nИсточник доказательства: {{evidence_source}} (см. приложение).\n\nУгрозы воспринял реально, опасаюсь за свою жизнь и здоровье.\n\nПрошу:\n1. Зарегистрировать настоящее заявление в КУИ.\n2. Провести проверку в порядке ст. 180 УПК РК.\n3. Принять решение в установленный законом срок.\n\n**Об ответственности по ст. 419 УК РК за заведомо ложный донос предупреждён.**\n\n«{{filing_date}}» 20__ г.   _________ {{applicant_full_name}}',
 '[{"name":"police_unit","label":"Орган МВД (например, УВД района ...)","required":true},{"name":"applicant_full_name","label":"ФИО заявителя","required":true},{"name":"applicant_address","label":"Адрес заявителя","required":true},{"name":"applicant_phone","label":"Телефон заявителя","required":true},{"name":"suspect_full_name","label":"ФИО подозреваемого","required":true},{"name":"incident_date","label":"Дата инцидента","required":true},{"name":"incident_time","label":"Время инцидента"},{"name":"incident_place","label":"Место инцидента","required":true},{"name":"threat_quote","label":"Цитата угрозы (из транскрипта ГС)","required":true,"multiline":true},{"name":"evidence_source","label":"Источник доказательства (ГС, документ)"},{"name":"filing_date","label":"Дата подачи"}]',
 '["УК РК:115","УК РК:419"]',
 true, now(), now()),
('tpl-civil-claim', 'CIVIL_CLAIM',
 'Исковое заявление (общая форма)',
 'Базовая форма искового заявления в суд.',
 'CLAIM',
 e'**В {{court_name}}**\n\nИстец: {{plaintiff_full_name}}\nАдрес: {{plaintiff_address}}\nТел.: {{plaintiff_phone}}\n\nОтветчик: {{defendant_full_name}}\nАдрес: {{defendant_address}}\n\nЦена иска: {{claim_amount}} тенге\nГоспошлина: {{state_fee}} тенге\n\n# ИСКОВОЕ ЗАЯВЛЕНИЕ\n## {{claim_subject}}\n\n## Обстоятельства дела\n\n{{facts}}\n\n## Правовое обоснование\n\n{{legal_basis}}\n\n## Просительная часть\n\nНа основании изложенного, руководствуясь {{cited_articles}}, прошу:\n\n{{relief_sought}}\n\n## Приложения\n\n{{attachments}}\n\n«{{filing_date}}» 20__ г.   _________ {{plaintiff_full_name}}',
 '[{"name":"court_name","label":"Суд","required":true},{"name":"plaintiff_full_name","label":"ФИО истца","required":true},{"name":"plaintiff_address","label":"Адрес истца","required":true},{"name":"plaintiff_phone","label":"Телефон истца"},{"name":"defendant_full_name","label":"ФИО ответчика","required":true},{"name":"defendant_address","label":"Адрес ответчика"},{"name":"claim_amount","label":"Цена иска (тг)"},{"name":"state_fee","label":"Госпошлина (тг)"},{"name":"claim_subject","label":"Предмет иска","required":true},{"name":"facts","label":"Обстоятельства дела","required":true,"multiline":true},{"name":"legal_basis","label":"Правовое обоснование","multiline":true},{"name":"cited_articles","label":"Ссылки на статьи (через запятую)"},{"name":"relief_sought","label":"Что просите","required":true,"multiline":true},{"name":"attachments","label":"Приложения","multiline":true},{"name":"filing_date","label":"Дата подачи"}]',
 '["ГК РК","ГПК РК"]',
 true, now(), now()),
('tpl-pretrial-claim', 'PRETRIAL_CLAIM',
 'Досудебная претензия',
 'Претензия контрагенту до обращения в суд.',
 'PRETRIAL',
 e'**Кому: {{addressee_full_name}}**\nАдрес: {{addressee_address}}\n\nОт: {{author_full_name}}\nАдрес: {{author_address}}\nТел.: {{author_phone}}\n\n# ДОСУДЕБНАЯ ПРЕТЕНЗИЯ\n\nМежду нами «{{contract_date}}» был заключён {{contract_subject}}.\n\nВ нарушение условий договора, {{breach_description}}.\n\nЭтим Вы нарушили {{cited_articles}}.\n\nТребую в срок до {{deadline}}:\n\n{{demands}}\n\nВ случае неисполнения буду вынужден обратиться в суд с взысканием суммы основного долга, неустойки, штрафа, судебных издержек.\n\n«{{filing_date}}» 20__ г.   _________ {{author_full_name}}',
 '[{"name":"addressee_full_name","label":"ФИО/наименование адресата","required":true},{"name":"addressee_address","label":"Адрес адресата","required":true},{"name":"author_full_name","label":"ФИО заявителя","required":true},{"name":"author_address","label":"Адрес заявителя","required":true},{"name":"author_phone","label":"Телефон заявителя"},{"name":"contract_date","label":"Дата договора","required":true},{"name":"contract_subject","label":"Предмет договора","required":true},{"name":"breach_description","label":"В чём нарушение","required":true,"multiline":true},{"name":"cited_articles","label":"Нарушенные статьи"},{"name":"deadline","label":"Срок исполнения","required":true},{"name":"demands","label":"Требования","required":true,"multiline":true},{"name":"filing_date","label":"Дата претензии"}]',
 '["ГК РК:272","ГК РК:282"]',
 true, now(), now())
on conflict (code) do nothing;

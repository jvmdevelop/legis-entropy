-- Create KZ Legislative Documents hierarchy
CREATE TABLE IF NOT EXISTS kz_legislative_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    code VARCHAR(50),
    document_number VARCHAR(100),
    full_text TEXT,
    date_published DATE,
    issued_by VARCHAR(255),
    date_enforced DATE,
    date_expired DATE,
    version INT NOT NULL DEFAULT 1,
    parent_document_id UUID REFERENCES kz_legislative_documents(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_kz_law_code ON kz_legislative_documents(code);
CREATE INDEX idx_kz_law_type ON kz_legislative_documents(document_type);
CREATE INDEX idx_kz_law_active ON kz_legislative_documents(is_active);
CREATE INDEX idx_kz_law_parent ON kz_legislative_documents(parent_document_id);

-- Create Articles table
CREATE TABLE IF NOT EXISTS articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legislative_document_id UUID NOT NULL REFERENCES kz_legislative_documents(id) ON DELETE CASCADE,
    article_number INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    full_text TEXT,
    notes TEXT,
    version INT NOT NULL DEFAULT 1,
    changed_at DATE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_article_law_number UNIQUE (legislative_document_id, article_number)
);

CREATE INDEX idx_article_law_id ON articles(legislative_document_id);
CREATE INDEX idx_article_number ON articles(article_number);
CREATE INDEX idx_article_active ON articles(is_active);

-- Create Clauses table (пункты статей)
CREATE TABLE IF NOT EXISTS clauses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    clause_number INT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clause_article_id ON clauses(article_id);

-- Create Subclauses table (подпункты)
CREATE TABLE IF NOT EXISTS subclauses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id UUID NOT NULL REFERENCES clauses(id) ON DELETE CASCADE,
    subclause_label VARCHAR(50) NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subclause_clause_id ON subclauses(clause_id);

-- Create KZ Regulations table
CREATE TABLE IF NOT EXISTS kz_regulations (
    id UUID PRIMARY KEY,
    regulation_type VARCHAR(50),
    FOREIGN KEY (id) REFERENCES kz_legislative_documents(id) ON DELETE CASCADE
);

-- Create Law Versions history table
CREATE TABLE IF NOT EXISTS law_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    law_id UUID NOT NULL REFERENCES kz_legislative_documents(id) ON DELETE CASCADE,
    version INT NOT NULL,
    change_description TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_law_id UUID REFERENCES kz_legislative_documents(id)
);

CREATE INDEX idx_law_versions_law_id ON law_versions(law_id);

-- Create Document Relationships table (for connecting user documents to laws)
CREATE TABLE IF NOT EXISTS document_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_doc_id UUID,
    target_law_id UUID NOT NULL REFERENCES kz_legislative_documents(id),
    relationship_type VARCHAR(50) NOT NULL,
    confidence FLOAT,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_relationships_source ON document_relationships(source_doc_id);
CREATE INDEX idx_doc_relationships_target ON document_relationships(target_law_id);
CREATE INDEX idx_doc_relationships_type ON document_relationships(relationship_type);

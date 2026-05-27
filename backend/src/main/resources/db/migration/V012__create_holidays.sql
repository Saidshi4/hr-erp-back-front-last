CREATE TABLE holidays (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    holiday_date DATE NOT NULL,
    apply_scope VARCHAR(20) NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE holiday_targets (
    id BIGSERIAL PRIMARY KEY,
    holiday_id BIGINT NOT NULL REFERENCES holidays(id) ON DELETE CASCADE,
    scope_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL
);

INSERT INTO holidays (tenant_id, name, description, holiday_date, apply_scope) VALUES
    (1, 'Novruz Bayramı', 'Bütün şirkət üzrə rəsmi bayram günü.', '2026-03-20', 'ALL'),
    (1, 'İstehsalat Günü', 'Operations departamenti üçün qeyri-iş günü.', '2026-05-12', 'SPECIFIC');

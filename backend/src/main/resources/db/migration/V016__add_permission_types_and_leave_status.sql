ALTER TABLE leaves ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE IF NOT EXISTS permission_types (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO permission_types (tenant_id, code, name, is_custom) VALUES
    (1, 'MEDICAL_LEAVE', 'Medical Leave', FALSE),
    (1, 'PREGNANCY_LEAVE', 'Pregnancy Leave', FALSE),
    (1, 'MATERNITY_LEAVE', 'Maternity Leave', FALSE),
    (1, 'PARENTAL_LEAVE', 'Parental Leave', FALSE),
    (1, 'REMOTE_WORK', 'Remote Work Permission', FALSE),
    (1, 'FLEXIBLE_HOURS', 'Flexible Hours', FALSE),
    (1, 'UNPAID_LEAVE', 'Unpaid Leave', FALSE),
    (1, 'SABBATICAL', 'Sabbatical', FALSE);

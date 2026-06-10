-- Create device_users table
CREATE TABLE IF NOT EXISTS device_users (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_no VARCHAR(50) NOT NULL,
    device_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_users_tenant ON device_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_device_users_employee_no ON device_users(employee_no);
CREATE INDEX IF NOT EXISTS idx_device_users_device_id ON device_users(device_id);

-- Create acs_raw_events table
CREATE TABLE IF NOT EXISTS acs_raw_events (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    serial_no BIGINT,
    event_time TIMESTAMP WITH TIME ZONE,
    major_event_type INTEGER,
    sub_event_type INTEGER,
    employee_no_string VARCHAR(255),
    card_no VARCHAR(255),
    raw_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_acs_raw_device_serial UNIQUE (device_id, serial_no)
);

CREATE INDEX IF NOT EXISTS idx_acs_raw_device ON acs_raw_events(device_id);
CREATE INDEX IF NOT EXISTS idx_acs_raw_event_time ON acs_raw_events(event_time);

-- Create attendance_punches table
CREATE TABLE IF NOT EXISTS attendance_punches (
    id BIGSERIAL PRIMARY KEY,
    raw_event_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    employee_no VARCHAR(50),
    punch_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_punches_raw_event FOREIGN KEY (raw_event_id) REFERENCES acs_raw_events(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attendance_punches_device ON attendance_punches(device_id);
CREATE INDEX IF NOT EXISTS idx_attendance_punches_employee ON attendance_punches(employee_no);
CREATE INDEX IF NOT EXISTS idx_attendance_punches_time ON attendance_punches(punch_time);

-- Create acs_failed_attempts table
CREATE TABLE IF NOT EXISTS acs_failed_attempts (
    id BIGSERIAL PRIMARY KEY,
    raw_event_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    identity VARCHAR(255),
    sub_event_type INTEGER,
    event_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_acs_failed_raw_event FOREIGN KEY (raw_event_id) REFERENCES acs_raw_events(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_acs_failed_device ON acs_failed_attempts(device_id);
CREATE INDEX IF NOT EXISTS idx_acs_failed_event_time ON acs_failed_attempts(event_time);

-- Create device_cursors table
CREATE TABLE IF NOT EXISTS device_cursors (
    device_id BIGINT PRIMARY KEY,
    last_serial_no BIGINT,
    last_event_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_cursors_updated ON device_cursors(updated_at);

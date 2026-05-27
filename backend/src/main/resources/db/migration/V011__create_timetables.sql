CREATE TABLE timetables (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_late_minutes INT NOT NULL DEFAULT 0,
    allowed_early_leave_minutes INT NOT NULL DEFAULT 0,
    shift_type VARCHAR(20) NOT NULL DEFAULT 'STANDART',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO timetables (
    tenant_id,
    name,
    description,
    start_time,
    end_time,
    crosses_midnight,
    allowed_late_minutes,
    allowed_early_leave_minutes,
    shift_type
) VALUES
    (1, 'Office Standard', 'Core office timetable for headquarters administrative teams.', '09:00', '18:00', FALSE, 10, 15, 'STANDART'),
    (1, 'Operations Shift', 'Flexible schedule for daily operational coverage.', '08:30', '17:30', FALSE, 20, 10, 'STANDART'),
    (1, 'Night Monitoring', 'Overnight shift for infrastructure and security monitoring.', '22:00', '07:00', TRUE, 5, 5, 'DEQIQ'),
    (1, 'Support Rotation', 'Customer support rotation timetable.', '10:00', '19:00', FALSE, 15, 10, 'SERBEST');

-- Insert head office branch
INSERT INTO branches (branch_name, branch_code, location, is_head_office)
VALUES ('Head Office', 'HO', 'Baku, Azerbaijan', TRUE)
ON CONFLICT (branch_code) DO NOTHING;

-- Insert HR department
INSERT INTO departments (department_name, branch_id)
SELECT 'Human Resources', id FROM branches WHERE branch_code = 'HO'
ON CONFLICT DO NOTHING;

-- Insert admin user (password: admin123 BCrypt hashed)
INSERT INTO users (username, email, password_hash, user_type, branch_id)
SELECT 'admin123', 'admin@hic.az', '$2y$12$4F6moAz.IWlGU3rdUUoQpuLUJ5KlKtA6oX3sUT2hV1/gjjugFqTQq', 'HEAD_OFFICE_HR', b.id
FROM branches b WHERE b.branch_code = 'HO'
ON CONFLICT (username) DO NOTHING;

-- Insert Azerbaijan Labour Code leave types
INSERT INTO leave_types (leave_code, leave_name, annual_entitlement, is_paid) VALUES
('ANNUAL', 'İllik məzuniyyət', 21, TRUE),
('SICK', 'Xəstəlik məzuniyyəti', 30, TRUE),
('MATERNITY', 'Hamiləlik və doğuş məzuniyyəti', 126, TRUE),
('PATERNITY', 'Ata məzuniyyəti', 14, TRUE),
('CHILDCARE', 'Uşağa qulluq məzuniyyəti', 365, FALSE),
('STUDY', 'Təhsil məzuniyyəti', 30, TRUE),
('MARRIAGE', 'Nikah məzuniyyəti', 5, TRUE),
('BEREAVEMENT', 'Matəm məzuniyyəti', 5, TRUE),
('UNPAID', 'Ödənişsiz məzuniyyət', NULL, FALSE),
('DISABILITY', 'Əlillik məzuniyyəti', 42, TRUE),
('MILITARY', 'Hərbi məzuniyyət', NULL, TRUE),
('ADDITIONAL', 'Əlavə məzuniyyət', 7, TRUE)
ON CONFLICT (leave_code) DO NOTHING;

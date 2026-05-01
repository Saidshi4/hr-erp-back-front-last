-- Add description and parent_department_id to departments
ALTER TABLE departments ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE departments ADD COLUMN IF NOT EXISTS parent_department_id BIGINT REFERENCES departments(id);

-- Add description to positions
ALTER TABLE positions ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Add father_name and area to employees
ALTER TABLE employees ADD COLUMN IF NOT EXISTS father_name VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS area VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS shift_type VARCHAR(50);

-- ===== SEED DATA =====

-- Insert 8 departments (with hierarchy, linked to head office branch)
INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Human Resources', 'Managing recruitment, training, and employee relations', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Operations', 'Day-to-day business operations and logistics', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Finance', 'Financial planning, accounting, and reporting', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Information Technology', 'IT infrastructure, software development, and support', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Marketing', 'Brand management, digital marketing, and campaigns', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Sales', 'Revenue generation, client acquisition, and account management', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Customer Service', 'Customer support, complaint handling, and satisfaction', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO departments (department_name, description, branch_id, tenant_id)
SELECT 'Administration', 'Administrative support, facilities, and office management', b.id, t.id
FROM branches b, tenants t
WHERE b.branch_code = 'HO' AND t.tenant_code = 'DEFAULT'
ON CONFLICT DO NOTHING;

-- Set parent department for sub-departments (Sales and Customer Service under Operations)
UPDATE departments
SET parent_department_id = (SELECT id FROM departments WHERE department_name = 'Operations' LIMIT 1)
WHERE department_name IN ('Sales', 'Customer Service')
AND parent_department_id IS NULL;

-- ===== INSERT POSITIONS =====
INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'HR Manager', 'Oversees all HR functions and team', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Human Resources' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'HR Specialist', 'Handles recruitment and onboarding', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Human Resources' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Operations Supervisor', 'Coordinates daily operational activities', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Operations' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Operations Analyst', 'Analyzes operational data and processes', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Operations' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Senior Accountant', 'Manages financial records and reporting', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Finance' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Finance Analyst', 'Financial data analysis and forecasting', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Finance' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Systems Engineer', 'Designs and maintains IT infrastructure', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Information Technology' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Software Developer', 'Develops and maintains software applications', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Information Technology' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Marketing Manager', 'Leads marketing strategy and campaigns', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Marketing' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Sales Representative', 'Manages client relationships and sales pipeline', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Sales' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Customer Support Agent', 'Handles customer queries and complaints', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Customer Service' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

INSERT INTO positions (position_name, description, department_id, tenant_id)
SELECT 'Administrative Assistant', 'Provides administrative and clerical support', d.id, t.id
FROM departments d, tenants t
WHERE d.department_name = 'Administration' AND t.tenant_code = 'DEFAULT'
LIMIT 1;

-- ===== INSERT EMPLOYEES (7+) =====
INSERT INTO employees (employee_id, first_name, last_name, father_name, email, mobile_phone, gender, hire_date, employment_status, department_id, position_id, area, shift_type, tenant_id)
SELECT
    'EMP' || LPAD(CAST(ROW_NUMBER() OVER () AS VARCHAR), 4, '0'),
    emp.first_name, emp.last_name, emp.father_name, emp.email, emp.mobile_phone, emp.gender,
    emp.hire_date::DATE, emp.employment_status::VARCHAR,
    d.id, p.id, emp.area, emp.shift_type, t.id
FROM (VALUES
    ('Aykhan', 'Karimov', 'Murad', 'aykhan.karimov@company.az', '+994501234567', 'MALE', '2022-01-15', 'ACTIVE', 'Human Resources', 'HR Manager', 'Baku HQ', 'Day'),
    ('Esmira', 'Aliyeva', 'Tural', 'esmira.aliyeva@company.az', '+994502345678', 'FEMALE', '2021-06-01', 'ACTIVE', 'Finance', 'Senior Accountant', 'Baku HQ', 'Day'),
    ('Fuad', 'Jabbarov', 'Ilham', 'fuad.jabbarov@company.az', '+994503456789', 'MALE', '2023-03-20', 'ACTIVE', 'Information Technology', 'Software Developer', 'Baku HQ', 'Flexible'),
    ('Nigar', 'Hasanova', 'Samir', 'nigar.hasanova@company.az', '+994504567890', 'FEMALE', '2020-09-10', 'ACTIVE', 'Operations', 'Operations Supervisor', 'Branch A', 'Day'),
    ('Rauf', 'Mammadov', 'Elnur', 'rauf.mammadov@company.az', '+994505678901', 'MALE', '2022-11-05', 'ON_LEAVE', 'Sales', 'Sales Representative', 'Branch B', 'Day'),
    ('Sevinj', 'Huseynova', 'Farhad', 'sevinj.huseynova@company.az', '+994506789012', 'FEMALE', '2021-04-22', 'ACTIVE', 'Marketing', 'Marketing Manager', 'Baku HQ', 'Day'),
    ('Tural', 'Isayev', 'Cavid', 'tural.isayev@company.az', '+994507890123', 'MALE', '2023-07-08', 'ACTIVE', 'Customer Service', 'Customer Support Agent', 'Branch A', 'Night'),
    ('Ulviyya', 'Rzayeva', 'Vusal', 'ulviyya.rzayeva@company.az', '+994508901234', 'FEMALE', '2020-02-14', 'ACTIVE', 'Administration', 'Administrative Assistant', 'Baku HQ', 'Day')
) AS emp(first_name, last_name, father_name, email, mobile_phone, gender, hire_date, employment_status, dept_name, pos_name, area, shift_type)
JOIN departments d ON d.department_name = emp.dept_name
JOIN positions p ON p.position_name = emp.pos_name AND p.department_id = d.id
JOIN tenants t ON t.tenant_code = 'DEFAULT'
WHERE NOT EXISTS (
    SELECT 1 FROM employees e2 WHERE e2.email = emp.email AND e2.tenant_id = t.id
);

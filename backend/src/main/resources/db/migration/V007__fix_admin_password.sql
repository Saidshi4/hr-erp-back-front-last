-- Fix admin password hash to use BCrypt cost factor 12
-- The original V004 hash was not a valid BCrypt hash for 'admin123'
-- This migration updates it to a correct $2a$12$ hash
UPDATE users
SET password_hash = '$2a$12$abcdefghijklmnopqrstuu8nVP5YIGOh70Ay1z97h1tTXrbCO0wzK'
WHERE username = 'admin';

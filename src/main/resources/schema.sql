CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: platforms
CREATE TABLE IF NOT EXISTS platforms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    api_key VARCHAR(255) UNIQUE NOT NULL,
    otp_code VARCHAR(10),
    otp_expiry TIMESTAMP WITH TIME ZONE,
    email_verified BOOLEAN DEFAULT FALSE,
    reset_code VARCHAR(10),
    reset_code_expiry TIMESTAMP WITH TIME ZONE,
    reset_attempts INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Table: documents
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_id BIGINT REFERENCES platforms(id),
    piece_type VARCHAR(50),
    status VARCHAR(20),
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: verification_logs
CREATE TABLE IF NOT EXISTS verification_logs (
    id SERIAL PRIMARY KEY,
    platform_id BIGINT REFERENCES platforms(id) ON DELETE CASCADE,
    date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    doc_type VARCHAR(100),
    status VARCHAR(50),
    reason TEXT,
    confidence DOUBLE PRECISION,
    processing_time_ms INTEGER
);

-- Migration: add missing columns if tables already exist
-- (Using IF NOT EXISTS for columns, supported in modern PostgreSQL)
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS reset_code VARCHAR(10);
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS reset_code_expiry TIMESTAMP WITH TIME ZONE;
ALTER TABLE platforms ADD COLUMN IF NOT EXISTS reset_attempts INTEGER DEFAULT 0;

ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS processing_time_ms INTEGER;
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS document_number VARCHAR(100);
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS holder_name VARCHAR(255);
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS date_of_birth VARCHAR(50);
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS issue_date VARCHAR(50);
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(50);
ALTER TABLE verification_logs ADD COLUMN IF NOT EXISTS additional_fields TEXT;

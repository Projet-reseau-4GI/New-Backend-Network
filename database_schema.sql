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
DO $$
BEGIN
    -- platforms new columns
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='platforms' AND column_name='password_hash') THEN
        ALTER TABLE platforms ADD COLUMN password_hash VARCHAR(255);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='platforms' AND column_name='email_verified') THEN
        ALTER TABLE platforms ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='platforms' AND column_name='reset_code') THEN
        ALTER TABLE platforms ADD COLUMN reset_code VARCHAR(10);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='platforms' AND column_name='reset_code_expiry') THEN
        ALTER TABLE platforms ADD COLUMN reset_code_expiry TIMESTAMP WITH TIME ZONE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='platforms' AND column_name='reset_attempts') THEN
        ALTER TABLE platforms ADD COLUMN reset_attempts INTEGER DEFAULT 0;
    END IF;
    -- verification_logs new columns
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='verification_logs' AND column_name='processing_time_ms') THEN
        ALTER TABLE verification_logs ADD COLUMN processing_time_ms INTEGER;
    END IF;
END
$$;

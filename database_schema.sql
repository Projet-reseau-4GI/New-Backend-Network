CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: platforms
CREATE TABLE IF NOT EXISTS platforms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    otp_code VARCHAR(10),
    otp_expiry TIMESTAMP WITH TIME ZONE,
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
    confidence DOUBLE PRECISION
);

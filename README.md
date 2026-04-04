# New-Backend-Network

## Document Verification & Multi-Tenant Management System

This project is a high-performance, reactive backend built with **Spring WebFlux** designed for B2B document verification. It features a multi-tenant architecture where organizational platforms can securely upload and analyze identity documents (ID Cards, Passports) using AI-powered extraction.

### 🚀 Key Features

*   **Reactive Architecture**: Built on top of Project Reactor for non-blocking, high-concurrency document processing.
*   **Multi-Tenancy (B2B)**: Native support for multiple platforms, each identified by a unique API Key.
*   **Advanced Document Analysis**:
    *   **OCR Integration**: Robust extraction of text from images and PDFs.
    *   **AI-Powered Insights**: Uses **Google Gemini** to extract and validate structured data (Name, DOB, Expiry, etc.) from noisy OCR text.
    *   **Cameroon ID Support**: Specialized logic for Cameroon identity documents.
*   **Secure Storage**: Automated file uploads to Supabase/S3-compatible storage.
*   **Platform Management**: Comprehensive admin APIs for platform lifecycle management and API key rotation.

### 🛠 Technical Stack

*   **Core**: Java 17+, Spring Boot 3, Spring WebFlux.
*   **Persistence**: PostgreSQL with R2DBC (Reactive Relational Database Connectivity).
*   **AI/ML**: Google Gemini (via Spring AI or custom integration).
*   **Storage**: Supabase Storage.
*   **Security**: API Key Authentication (`X-API-KEY`).

### 🔒 Security Model

The system has moved from a user-centric JWT model to a **B2B Platform model**:

1.  **Admin Endpoints**: `/api/admin/**` (Management of platforms).
2.  **Tenant Endpoints**: `/api/kernel/**` and core analysis APIs require a valid `X-API-KEY` header.
3.  **Tenant Context**: The platform identity is automatically propagated through the reactive stream context.

### 📖 API Reference

#### 1. Administration (Platform Management)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/admin/platforms` | Create a new tenant platform |
| `GET` | `/api/admin/platforms` | List all registered platforms |
| `POST` | `/api/admin/platforms/{id}/generate-key` | Rotate/Generate a new API key |
| `POST` | `/api/admin/platforms/{id}/toggle-status` | Activate/Deactivate a platform |

#### 2. Document Analysis (Tenant API)

**Endpoint**: `POST /api/documents/upload-analyze`
**Headers**: `X-API-KEY: your_platform_api_key`
**Body** (multipart/form-data):
*   `frontFile`: (Binary) Front side of the ID document.
*   `backFile`: (Binary, optional) Back side of the ID document.
*   `pieceType`: (String, optional) Hint for the document type (e.g., `ID_CARD`, `PASSPORT`).

### ⚙️ Getting Started

#### Prerequisites
*   Java 17 or higher.
*   PostgreSQL (with PostGIS support suggested).
*   Maven.
*   Configured API keys for Gemini and Supabase in `application.properties`.

#### Local Setup
1.  **Clone the repository**.
2.  **Initialize the database**: Run `database_schema.sql` to create the necessary tables.
3.  **Configure environment**: Update `src/main/resources/application.properties` with your credentials.
4.  **Run the application**:
    ```bash
    mvn spring-boot:run
    ```

### 📂 Project Structure
*   `Projects.Network.controller`: Reactive REST controllers.
*   `Projects.Network.service`: Business logic (Analysis, Storage, Platform management).
*   `Projects.Network.config`: Security filters and reactive context helpers.
*   `Projects.Network.repository`: R2DBC repositories for reactive persistence.


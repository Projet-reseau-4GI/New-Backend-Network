package com.projects.adapter.in.web.dto;

import lombok.Data;
import java.util.UUID;

/**
 * DocumentAnalysisRequest
 *
 * Request DTO for document analysis operations.
 * Contains the identifier of the document to be analyzed.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-06
 */
@Data
public class DocumentAnalysisRequest {

    /**
     * Unique identifier of the document to analyze.
     */
    private UUID documentId;
}

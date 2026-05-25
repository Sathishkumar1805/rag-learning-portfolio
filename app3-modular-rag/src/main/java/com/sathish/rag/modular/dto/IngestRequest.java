/**
 * Request DTO for document ingestion operations.
 *
 * <p>Carries the raw document text, an optional source identifier, and optional
 * metadata key-value pairs to attach to each generated chunk.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestRequest {

    @NotBlank(message = "Content must not be blank")
    private String content;

    private String source;

    private Map<String, String> metadata;
}

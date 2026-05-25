/**
 * Response DTO returned after a document ingestion operation completes.
 *
 * <p>Reports the number of child chunks and parent chunks created, along with
 * a status message and the source identifier for traceability.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestResponse {

    private String status;

    private String source;

    private int childChunksCreated;

    private int parentChunksCreated;

    private String message;
}

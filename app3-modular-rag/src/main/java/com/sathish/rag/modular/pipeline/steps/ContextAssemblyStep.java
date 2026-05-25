/**
 * Pipeline step that assembles the context block to inject into the LLM prompt.
 *
 * <p>Reads selected chunks from the {@link PipelineContext}, optionally expands each chunk
 * with its parent text (stored in {@code metadata.parentText}), deduplicates the passages,
 * and formats them into a numbered context block written back to the context.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline.steps;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContextAssemblyStep implements RagPipelineStep {

    private final RagProperties ragProperties;

    @Override
    public String getStepName() {
        return "ContextAssembly";
    }

    @Override
    public void execute(PipelineContext context) {
        log.debug("ContextAssemblyStep.execute() selectedChunks={}",
                context.getSelectedChunks().size());

        List<Document> chunks = context.getSelectedChunks().isEmpty()
                ? context.getCandidates()
                : context.getSelectedChunks();

        if (chunks.isEmpty()) {
            context.setContextBlock("");
            log.debug("ContextAssemblyStep: no chunks — empty context block");
            return;
        }

        boolean useParentContext = ragProperties.getGeneration().isIncludeParentContext();
        Set<String> seen = new LinkedHashSet<>();
        List<String> passages = new ArrayList<>();

        for (Document doc : chunks) {
            String text = resolveText(doc, useParentContext);
            if (text != null && !text.isBlank() && seen.add(text)) {
                passages.add(text);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            sb.append("[").append(i + 1).append("]\n");
            sb.append(passages.get(i).trim()).append("\n\n");
        }

        context.setContextBlock(sb.toString().trim());
        log.debug("ContextAssemblyStep: assembled {} passages, contextBlock length={}",
                passages.size(), context.getContextBlock().length());
    }

    private String resolveText(Document doc, boolean useParentContext) {
        if (useParentContext && doc.getMetadata() != null) {
            Object parentText = doc.getMetadata().get("parentText");
            if (parentText instanceof String pt && !pt.isBlank()) {
                return pt;
            }
        }
        return doc.getText();
    }
}

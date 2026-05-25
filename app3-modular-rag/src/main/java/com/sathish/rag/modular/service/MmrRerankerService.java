package com.sathish.rag.modular.service;

import com.sathish.rag.modular.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MmrRerankerService {

    private final RagProperties ragProperties;
    private final EmbeddingModel embeddingModel;

    public record MmrResult(
            List<Document> selectedDocuments,
            List<Document> allCandidates,
            double lambdaUsed,
            boolean mmrApplied) {
    }

    /**
     * Re-ranks candidates using MMR. Falls back to top-k if disabled or no candidates.
     *
     * @param candidates     pool of candidate documents from similarity search
     * @param queryEmbedding embedding of the query (or HyDE hypothesis)
     * @return MmrResult with selected documents and metadata
     */
    public MmrResult rerank(List<Document> candidates, List<Double> queryEmbedding) {
        log.debug("ENTER MMR rerank candidates={}", candidates.size());

        RagProperties.MmrProperties mmrCfg = ragProperties.getMmr();
        double lambda = mmrCfg.getLambda();
        int finalTopK = mmrCfg.getFinalTopK();

        if (!mmrCfg.isEnabled() || candidates.isEmpty()) {
            log.debug("MMR disabled or no candidates — returning top {} directly", finalTopK);
            List<Document> topK = candidates.stream().limit(finalTopK).toList();
            return new MmrResult(topK, candidates, lambda, false);
        }

        // Embed all candidate documents
        List<double[]> candidateEmbeddings = new ArrayList<>();
        for (Document doc : candidates) {
            try {
                float[] floatArr = embeddingModel.embed(doc.getText());
                double[] doubleArr = new double[floatArr.length];
                for (int i = 0; i < floatArr.length; i++) {
                    doubleArr[i] = floatArr[i];
                }
                candidateEmbeddings.add(doubleArr);
            } catch (Exception e) {
                log.warn("Failed to embed candidate doc, using zero vector: {}", e.getMessage());
                candidateEmbeddings.add(new double[queryEmbedding != null ? queryEmbedding.size() : 768]);
            }
        }

        double[] queryVec = queryEmbedding != null
                ? queryEmbedding.stream().mapToDouble(Double::doubleValue).toArray()
                : new double[candidateEmbeddings.get(0).length];

        // Greedy MMR selection
        List<Integer> selectedIndices = new ArrayList<>();
        List<Integer> remainingIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            remainingIndices.add(i);
        }

        while (selectedIndices.size() < finalTopK && !remainingIndices.isEmpty()) {
            int bestIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int idx : remainingIndices) {
                double[] docVec = candidateEmbeddings.get(idx);
                double relevance = cosineSimilarity(docVec, queryVec);

                double maxRedundancy = 0.0;
                for (int selIdx : selectedIndices) {
                    double sim = cosineSimilarity(docVec, candidateEmbeddings.get(selIdx));
                    if (sim > maxRedundancy) maxRedundancy = sim;
                }

                double mmrScore = lambda * relevance - (1.0 - lambda) * maxRedundancy;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestIdx = idx;
                }
            }

            if (bestIdx >= 0) {
                selectedIndices.add(bestIdx);
                remainingIndices.remove(Integer.valueOf(bestIdx));
                log.debug("MMR selected doc idx={} score={}", bestIdx, bestScore);
            } else {
                break;
            }
        }

        List<Document> selected = selectedIndices.stream().map(candidates::get).toList();
        log.debug("EXIT MMR rerank selected={}/{} lambda={}", selected.size(), candidates.size(), lambda);
        return new MmrResult(selected, candidates, lambda, true);
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double mag = Math.sqrt(normA) * Math.sqrt(normB);
        return mag == 0.0 ? 0.0 : dot / mag;
    }
}

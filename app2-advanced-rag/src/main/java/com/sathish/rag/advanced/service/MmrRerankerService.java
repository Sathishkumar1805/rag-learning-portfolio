/**
 * MmrRerankerService.java
 * <p><b>RAG Role:</b> Implements Maximal Marginal Relevance (MMR) re-ranking to select
 * a diverse yet relevant subset of retrieved chunks. MMR balances relevance to the query
 * against redundancy with already-selected chunks, preventing the LLM from receiving
 * five near-duplicate chunks that all say the same thing.
 * <p><b>Learning Note:</b> MMR was proposed by Carbonell and Goldstein (1998) for
 * document summarisation. The lambda parameter controls the diversity/relevance trade-off:
 * lambda=1.0 is pure relevance (like top-k), lambda=0.0 is pure diversity.
 * Typical values: 0.5-0.7. Re-ranking is done in O(n*k) cosine similarity operations.
 * <p><b>LEARN:</b> App 1 returns the top-k most similar chunks regardless of redundancy.
 * App 2's MMR ensures the selected chunks collectively cover different aspects of the answer,
 * improving LLM response quality especially when the document collection has many similar passages.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.service;

import com.sathish.rag.advanced.config.RagProperties;
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

    /**
     * Result record for MMR re-ranking.
     *
     * @param selectedDocuments The diverse, relevance-balanced subset chosen by MMR
     * @param allCandidates     All input candidate documents (before re-ranking)
     * @param lambdaUsed        The lambda value applied
     * @param mmrApplied        Whether MMR was actually applied or fell back to top-k
     */
    public record MmrResult(
            List<Document> selectedDocuments,
            List<Document> allCandidates,
            Double lambdaUsed,
            boolean mmrApplied) {
    }

    /**
     * Re-ranks the candidate documents using the MMR algorithm.
     * If disabled, returns the top finalTopK candidates directly without re-ranking.
     *
     * @param candidates         Pool of candidate documents from similarity search
     * @param queryEmbedding     Embedding of the query (or HyDE hypothesis)
     * @param lambdaOverride     Optional per-request lambda override
     * @param mmrEnabledOverride Optional per-request enable/disable override
     * @param finalTopKOverride  Optional per-request final top-k override
     * @return MmrResult with selected documents and metadata
     */
    public MmrResult rerank(
            List<Document> candidates,
            List<Double> queryEmbedding,
            Double lambdaOverride,
            Boolean mmrEnabledOverride,
            Integer finalTopKOverride) {

        log.debug("ENTER MMR rerank candidates={} lambdaOverride={} mmrOverride={} topKOverride={}",
                candidates.size(), lambdaOverride, mmrEnabledOverride, finalTopKOverride);

        RagProperties.MmrProperties mmrCfg = ragProperties.getMmr();

        double lambda = lambdaOverride != null ? lambdaOverride : mmrCfg.getLambda();
        boolean enabled = mmrEnabledOverride != null ? mmrEnabledOverride : mmrCfg.isEnabled();
        int finalTopK = finalTopKOverride != null ? finalTopKOverride : mmrCfg.getFinalTopK();

        if (!enabled || candidates.isEmpty()) {
            log.debug("MMR disabled or no candidates — returning top {} directly", finalTopK);
            List<Document> topK = candidates.stream()
                    .limit(finalTopK)
                    .toList();
            return new MmrResult(topK, candidates, lambda, false);
        }

        // Embed all candidate documents
        long embedStart = System.currentTimeMillis();
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
                candidateEmbeddings.add(new double[queryEmbedding.size()]);
            }
        }
        log.debug("Embedded {} candidates in {}ms", candidates.size(),
                System.currentTimeMillis() - embedStart);

        // Convert query embedding to double[]
        double[] queryVec = queryEmbedding.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

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

                // Relevance: cosine similarity with query
                double relevance = cosineSimilarity(docVec, queryVec);

                // Redundancy: max cosine similarity with already selected docs
                double maxRedundancy = 0.0;
                for (int selIdx : selectedIndices) {
                    double sim = cosineSimilarity(docVec, candidateEmbeddings.get(selIdx));
                    if (sim > maxRedundancy) {
                        maxRedundancy = sim;
                    }
                }

                // MMR score = lambda * relevance - (1 - lambda) * maxRedundancy
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

        List<Document> selected = selectedIndices.stream()
                .map(candidates::get)
                .toList();

        log.debug("EXIT MMR rerank selected={}/{} candidates lambda={}",
                selected.size(), candidates.size(), lambda);

        return new MmrResult(selected, candidates, lambda, true);
    }

    /**
     * Computes the cosine similarity between two vectors.
     * Returns 0.0 if either vector has zero magnitude.
     *
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity in range [-1.0, 1.0], or 0.0 for zero-norm vectors
     */
    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double magnitude = Math.sqrt(normA) * Math.sqrt(normB);
        if (magnitude == 0.0) {
            return 0.0;
        }

        return dotProduct / magnitude;
    }
}

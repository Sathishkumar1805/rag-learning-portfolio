/**
 * Main entry point for the Modular RAG application.
 *
 * <p>App 3 of the RAG Learning Portfolio. Demonstrates a composable, modular RAG pipeline
 * where each stage (retrieval, re-ranking, context assembly, generation) is a swappable
 * Spring bean. The active strategy is selected via application.yml with zero code changes.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ModularRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModularRagApplication.class, args);
    }
}

/**
 * AdvancedRagApplication.java
 * <p><b>RAG Role:</b> Spring Boot entry point for the Advanced RAG application.
 * Bootstraps the application context, enables configuration properties binding,
 * and starts the embedded Tomcat server.
 * <p><b>Learning Note:</b> @EnableConfigurationProperties ensures that @ConfigurationProperties
 * classes (like RagProperties) are registered as Spring beans even without @Component.
 * This is the recommended approach for type-safe configuration in production apps.
 * <p><b>LEARN:</b> Compare with App 1 (Naive RAG) — App 2 adds advanced retrieval techniques
 * (HyDE, parent-child chunking, MMR) on top of the same Spring AI foundation.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced;

import com.sathish.rag.advanced.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class AdvancedRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdvancedRagApplication.class, args);
    }
}

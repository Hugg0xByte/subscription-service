package com.globo.subscription.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides GCP beans for local development with the Pub/Sub emulator.
 * Bypasses real GCP authentication by supplying NoCredentialsProvider
 * and a static project ID.
 */
@Configuration
public class GcpEmulatorConfig {

    @Value("${spring.cloud.gcp.project-id:local-project}")
    private String projectId;

    @Bean
    public GcpProjectIdProvider gcpProjectIdProvider() {
        return () -> projectId;
    }

    @Bean
    public CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }
}

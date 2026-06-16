package com.globo.subscription.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Provides GCP beans that replace the excluded GcpContextAutoConfiguration.
 * Handles both local (emulator, no credentials) and cloud (real credentials) profiles.
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
    @Profile("!cloud")
    public CredentialsProvider noCredentialsProvider() {
        return NoCredentialsProvider.create();
    }

    @Bean
    @Profile("cloud")
    public CredentialsProvider googleCredentialsProvider() {
        return () -> GoogleCredentials.getApplicationDefault();
    }
}

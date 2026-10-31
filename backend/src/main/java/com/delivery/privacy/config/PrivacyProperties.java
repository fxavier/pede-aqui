package com.delivery.privacy.config;

import com.delivery.privacy.entity.ConsentPurpose;
import com.delivery.privacy.service.RetentionDataClass;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.privacy.*} (LAC-19/LAC-63, W7-C): the account-deletion reflection period and job batch, the current
 * version of each consent text, the locales a profile may choose and the retention period of every data class purged
 * by the {@code retention-cleaner} job. All periods are configurable defaults taken from the gaps register and are
 * still "a validar com jurista" (Mozambique's personal data protection law is a bill, 2026).
 */
@Component
@ConfigurationProperties(prefix = "app.privacy")
public class PrivacyProperties {
    private final Deletion deletion = new Deletion();
    private final Retention retention = new Retention();
    /** Current version of each consent text; granting an older version is refused. */
    private Map<ConsentPurpose, String> consentVersions = new EnumMap<>(Map.of(
            ConsentPurpose.TERMS_OF_SERVICE, "2026-10",
            ConsentPurpose.PRIVACY_POLICY, "2026-10",
            ConsentPurpose.MARKETING, "2026-10"));
    /** Locales a user may pick as preferred locale (PATCH /me). */
    private java.util.List<String> supportedLocales = java.util.List.of("pt-PT", "pt-MZ", "en");

    public Deletion getDeletion() { return deletion; }
    public Retention getRetention() { return retention; }
    public Map<ConsentPurpose, String> getConsentVersions() { return consentVersions; }
    public void setConsentVersions(Map<ConsentPurpose, String> consentVersions) { this.consentVersions = new EnumMap<>(consentVersions); }
    public java.util.List<String> getSupportedLocales() { return supportedLocales; }
    public void setSupportedLocales(java.util.List<String> supportedLocales) { this.supportedLocales = supportedLocales; }

    /** Account deletion (LAC-19). */
    public static class Deletion {
        /** Time between the request and the pseudonymisation, during which the customer can cancel. */
        private Duration reflectionPeriod = Duration.ofDays(30);
        /** Due requests handled per job run (oldest first). */
        private int batchSize = 50;

        public Duration getReflectionPeriod() { return reflectionPeriod; }
        public void setReflectionPeriod(Duration reflectionPeriod) { this.reflectionPeriod = reflectionPeriod; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    /** Retention cleaner (LAC-63): batch shape and the period of each data class. */
    public static class Retention {
        /** Rows deleted per statement/transaction. */
        private int batchSize = 5000;
        /** Upper bound of batches per data class per run (the rest waits for the next run). */
        private int maxBatchesPerClass = 200;
        /** Per data class: enabled flag and retention period (defaults in {@link RetentionDataClass}). */
        private Map<String, ClassPolicy> classes = new LinkedHashMap<>();

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxBatchesPerClass() { return maxBatchesPerClass; }
        public void setMaxBatchesPerClass(int maxBatchesPerClass) { this.maxBatchesPerClass = maxBatchesPerClass; }
        public Map<String, ClassPolicy> getClasses() { return classes; }
        public void setClasses(Map<String, ClassPolicy> classes) { this.classes = classes; }

        /** Effective policy of a data class: configured values over the class defaults. */
        public ClassPolicy policyOf(RetentionDataClass dataClass) {
            ClassPolicy configured = classes.get(dataClass.key());
            ClassPolicy effective = new ClassPolicy();
            effective.setEnabled(configured == null || configured.isEnabled());
            effective.setRetention(configured != null && configured.getRetention() != null
                    ? configured.getRetention() : dataClass.defaultRetention());
            return effective;
        }
    }

    /** Enabled flag and retention period of one data class. */
    public static class ClassPolicy {
        private boolean enabled = true;
        private Duration retention;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getRetention() { return retention; }
        public void setRetention(Duration retention) { this.retention = retention; }
    }
}

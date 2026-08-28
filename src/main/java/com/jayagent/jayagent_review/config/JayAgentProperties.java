package com.jayagent.jayagent_review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jayagent")
public class JayAgentProperties {

    private KnowledgeBase knowledgeBase = new KnowledgeBase();
    private Scoring scoring = new Scoring();
    private Webhook webhook = new Webhook();
    private ExternalApi externalApi = new ExternalApi();
    private Diff diff = new Diff();

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public ExternalApi getExternalApi() {
        return externalApi;
    }

    public void setExternalApi(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    public Diff getDiff() { return diff; }
    public void setDiff(Diff diff) { this.diff = diff; }

    public static class Diff {
        private int maxFileChars = 20000;
        private int maxTotalChars = 100000;
        private int pageSize = 100;
        private int maxPages = 20;
        public int getMaxFileChars() { return maxFileChars; }
        public void setMaxFileChars(int value) { maxFileChars = value; }
        public int getMaxTotalChars() { return maxTotalChars; }
        public void setMaxTotalChars(int value) { maxTotalChars = value; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int value) { pageSize = value; }
        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int value) { maxPages = value; }
    }

    public static class KnowledgeBase {
        private boolean autoInit = true;
        private String sourcePath = "knowledge/java_security_rules.txt";
        private String stateFile = "data/knowledge-base-state.txt";
        private double minScore = 0.3;

        public boolean isAutoInit() {
            return autoInit;
        }

        public void setAutoInit(boolean autoInit) {
            this.autoInit = autoInit;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public String getStateFile() {
            return stateFile;
        }

        public void setStateFile(String stateFile) {
            this.stateFile = stateFile;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }
    }

    public static class Scoring {
        private int criticalPenalty = 25;
        private int highPenalty = 10;
        private int mediumPenalty = 5;
        private int lowPenalty = 1;
        private double confidenceWeight = 0.5;
        private double duplicateRiskFactor = 0.5;

        public int getCriticalPenalty() {
            return criticalPenalty;
        }

        public void setCriticalPenalty(int criticalPenalty) {
            this.criticalPenalty = criticalPenalty;
        }

        public int getHighPenalty() {
            return highPenalty;
        }

        public void setHighPenalty(int highPenalty) {
            this.highPenalty = highPenalty;
        }

        public int getMediumPenalty() {
            return mediumPenalty;
        }

        public void setMediumPenalty(int mediumPenalty) {
            this.mediumPenalty = mediumPenalty;
        }

        public int getLowPenalty() {
            return lowPenalty;
        }

        public void setLowPenalty(int lowPenalty) {
            this.lowPenalty = lowPenalty;
        }

        public double getConfidenceWeight() { return confidenceWeight; }
        public void setConfidenceWeight(double confidenceWeight) { this.confidenceWeight = confidenceWeight; }
        public double getDuplicateRiskFactor() { return duplicateRiskFactor; }
        public void setDuplicateRiskFactor(double duplicateRiskFactor) { this.duplicateRiskFactor = duplicateRiskFactor; }
    }

    public static class Webhook {
        private String dedupStore = "data/webhook-events.log";
        private String dedupStoreType = "file";
        private boolean strictMode = true;
        private int maxBodyLength = 100000;
        private boolean requireEventId = true;
        private boolean allowSharedSecretFallback = true;
        private Dedup dedup = new Dedup();

        public String getDedupStore() {
            return dedupStore;
        }

        public void setDedupStore(String dedupStore) {
            this.dedupStore = dedupStore;
        }

        public String getDedupStoreType() {
            return dedupStoreType;
        }

        public void setDedupStoreType(String dedupStoreType) {
            this.dedupStoreType = dedupStoreType;
        }

        public boolean isStrictMode() {
            return strictMode;
        }

        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }

        public boolean isRequireEventId() {
            return requireEventId;
        }

        public void setRequireEventId(boolean requireEventId) {
            this.requireEventId = requireEventId;
        }

        public boolean isAllowSharedSecretFallback() {
            return allowSharedSecretFallback;
        }

        public void setAllowSharedSecretFallback(boolean allowSharedSecretFallback) {
            this.allowSharedSecretFallback = allowSharedSecretFallback;
        }

        public Dedup getDedup() {
            return dedup;
        }

        public void setDedup(Dedup dedup) {
            this.dedup = dedup;
        }
    }

    public static class Dedup {
        private long ttlDays = 30;

        public long getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(long ttlDays) {
            this.ttlDays = ttlDays;
        }
    }

    public static class ExternalApi {
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 10000;
        private int maxRetries = 2;
        private long retryBackoffMillis = 300;

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMillis() {
            return retryBackoffMillis;
        }

        public void setRetryBackoffMillis(long retryBackoffMillis) {
            this.retryBackoffMillis = retryBackoffMillis;
        }
    }
}

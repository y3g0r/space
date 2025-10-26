package com.diskanalyzer.config;

/**
 * Feature flags for enabling/disabling application features.
 */
public class FeatureFlags {
    /**
     * Enable/disable caching functionality.
     * When disabled:
     * - Scan results will not be cached
     * - Cache loading will be skipped
     * - Clear Cache button will be hidden
     */
    public static final boolean ENABLE_CACHING = false;

    /**
     * Private constructor to prevent instantiation.
     */
    private FeatureFlags() {
        throw new AssertionError("FeatureFlags should not be instantiated");
    }
}

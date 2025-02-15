package org.gait.dto;

public final class CacheOntology {
    public static final String NS = "http://example.org/cache#";
    public static final String CachedEntry = NS + "CachedEntry";
    public static final String originalPrompt = NS + "originalPrompt";
    public static final String hasNLPResponse = NS + "hasNLPResponse";
    public static final String hasSPARQLQuery = NS + "hasSPARQLQuery";

    private CacheOntology() {
        // Prevent instantiation
    }
}

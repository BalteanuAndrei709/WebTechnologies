package org.gait.service;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.gait.dto.CacheOntology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class BlazegraphCacheService {

    @Value("${blazegraph.endpoint:http://localhost:9999/blazegraph/namespace/kb/sparql}")
    private String blazegraphEndpoint;

    // Prefixes for our cache ontology and XSD.
    private static final String PREFIXES = "PREFIX cache: <" + CacheOntology.NS + "> " +
            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> ";

    /**
     * Generates a unique URI for a given prompt.
     * (For production, consider using a cryptographic hash.)
     */
    public String generatePromptURI(String prompt) {
        return "urn:prompt:" + URLEncoder.encode(prompt, StandardCharsets.UTF_8);
    }

    /**
     * Escapes double quotes and removes newlines from the input.
     */
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /**
     * Caches the provided data in Blazegraph along with the current timestamp.
     */
    public void saveCacheEntry(String prompt, String nlpResponse, String sparqlQuery, String graphQLResult) {
        String promptURI = generatePromptURI(prompt);
        String safePrompt = sanitize(prompt);
        String safeNlpResponse = sanitize(nlpResponse);
        String safeSparqlQuery = sanitize(sparqlQuery);
        String safeGraphQLResult = sanitize(graphQLResult);
        String timestamp = Instant.now().toString();  // ISO-8601

        String updateString = PREFIXES +
                "INSERT DATA { " +
                "  <" + promptURI + "> a <" + CacheOntology.CachedEntry + "> ; " +
                "    <" + CacheOntology.originalPrompt + "> \"" + safePrompt + "\" ; " +
                "    <" + CacheOntology.hasNLPResponse + "> \"" + safeNlpResponse + "\" ; " +
                "    <" + CacheOntology.hasSPARQLQuery + "> \"" + safeSparqlQuery + "\" ; " +
                "    <" + CacheOntology.hasGraphQLResult + "> \"" + safeGraphQLResult + "\" ; " +
                "    <" + CacheOntology.createdAt + "> \"" + timestamp + "\"^^xsd:dateTime ." +
                "}";
        UpdateRequest updateRequest = UpdateFactory.create(updateString);
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(updateRequest, blazegraphEndpoint);
        processor.execute();
    }

    /**
     * Retrieves the cached entry for the given prompt.
     * If the entry is older than 10 minutes, it is considered expired,
     * deleted, and null is returned.
     */
    public CachedEntry fetchCacheEntry(String prompt) {
        String promptURI = generatePromptURI(prompt);
        String queryString = PREFIXES +
                "SELECT ?nlpResponse ?sparqlQuery ?graphQLResult ?createdAt WHERE { " +
                "  <" + promptURI + "> a <" + CacheOntology.CachedEntry + "> ; " +
                "    <" + CacheOntology.hasNLPResponse + "> ?nlpResponse ; " +
                "    <" + CacheOntology.hasSPARQLQuery + "> ?sparqlQuery ; " +
                "    <" + CacheOntology.hasGraphQLResult + "> ?graphQLResult ; " +
                "    <" + CacheOntology.createdAt + "> ?createdAt ." +
                "}";
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionHTTP.newBuilder()
                .endpoint(blazegraphEndpoint)
                .query(query)
                .sendMode(QuerySendMode.asPost)
                .build()) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                String nlpResp = sol.getLiteral("nlpResponse").getString();
                String sparqlQ = sol.getLiteral("sparqlQuery").getString();
                String graphQLResult = sol.getLiteral("graphQLResult").getString();
                String createdAtStr = sol.getLiteral("createdAt").getString();
                Instant createdAt = Instant.parse(createdAtStr);
                // Check expiration (10 minutes).
                if (Duration.between(createdAt, Instant.now()).toMinutes() >= 10) {
                    removeCacheEntry(prompt);
                    return null;
                }
                return new CachedEntry(prompt, nlpResp, sparqlQ, graphQLResult);
            }
        }
        return null;
    }

    /**
     * Deletes the cached entry for the specified prompt.
     */
    public void removeCacheEntry(String prompt) {
        String promptURI = generatePromptURI(prompt);
        String updateString = PREFIXES +
                "DELETE WHERE { <" + promptURI + "> ?p ?o . }";
        UpdateRequest updateRequest = UpdateFactory.create(updateString);
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(updateRequest, blazegraphEndpoint);
        processor.execute();
    }

    /**
     * DTO representing a cached entry.
     */
    public static class CachedEntry {
        public final String prompt;
        public final String nlpResponse;
        public final String sparqlQuery;
        public final String graphQLResult;

        public CachedEntry(String prompt, String nlpResponse, String sparqlQuery, String graphQLResult) {
            this.prompt = prompt;
            this.nlpResponse = nlpResponse;
            this.sparqlQuery = sparqlQuery;
            this.graphQLResult = graphQLResult;
        }
    }
}

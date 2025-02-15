package org.gait.service;

import org.apache.jena.query.*;
import org.apache.jena.update.*;
import org.gait.dto.CacheOntology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@Service
public class BlazegraphCacheService {

    // Blazegraph SPARQL endpoint URL (adjust if necessary)
    @Value("${blazegraph.endpoint:http://localhost:9999/blazegraph/namespace/kb/sparql}")
    private String blazegraphEndpoint;

    // Build SPARQL prefix using our vocabulary namespace
    private static final String PREFIXES = "PREFIX cache: <" + CacheOntology.NS + "> ";

    /**
     * Generates a unique URI for the given prompt.
     * In production, consider using a proper hash.
     */
    public String generatePromptURI(String prompt) throws UnsupportedEncodingException {
        return "urn:prompt:" + URLEncoder.encode(prompt, "UTF-8");
    }

    /**
     * Inserts a cache entry with the given prompt, NLP response, and SPARQL query.
     */
    public void cachePrompt(String prompt, String nlpResponse, String sparqlQuery) throws Exception {
        String promptURI = generatePromptURI(prompt);

        // Escape quotes and remove newline/carriage return characters.
        String escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
        String escapedNlpResponse = nlpResponse.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
        String escapedSparqlQuery = sparqlQuery.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");

        String updateString = PREFIXES +
                "INSERT DATA { " +
                "  <" + promptURI + "> a <" + CacheOntology.CachedEntry + "> ; " +
                "    <" + CacheOntology.originalPrompt + "> \"" + escapedPrompt + "\" ; " +
                "    <" + CacheOntology.hasNLPResponse + "> \"" + escapedNlpResponse + "\" ; " +
                "    <" + CacheOntology.hasSPARQLQuery + "> \"" + escapedSparqlQuery + "\" ." +
                "}";

        UpdateRequest updateRequest = UpdateFactory.create(updateString);
        UpdateProcessor processor = UpdateExecutionFactory.createRemote(updateRequest, blazegraphEndpoint);
        processor.execute();
    }


    /**
     * Retrieves a cached entry (if any) for the given prompt.
     */
    public CachedEntry getCachedEntry(String prompt) throws Exception {
        String promptURI = generatePromptURI(prompt);
        String queryString = PREFIXES +
                "SELECT ?nlpResponse ?sparqlQuery WHERE { " +
                "  <" + promptURI + "> a <" + CacheOntology.CachedEntry + "> ; " +
                "    <" + CacheOntology.hasNLPResponse + "> ?nlpResponse ; " +
                "    <" + CacheOntology.hasSPARQLQuery + "> ?sparqlQuery ." +
                "}";
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(blazegraphEndpoint, query)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                String nlpResp = sol.getLiteral("nlpResponse").getString();
                String sparqlQ = sol.getLiteral("sparqlQuery").getString();
                return new CachedEntry(prompt, nlpResp, sparqlQ);
            }
        }
        return null; // No cached entry found.
    }

    /**
     * Simple DTO to hold cached data.
     */
    public static class CachedEntry {
        public final String prompt;
        public final String nlpResponse;
        public final String sparqlQuery;

        public CachedEntry(String prompt, String nlpResponse, String sparqlQuery) {
            this.prompt = prompt;
            this.nlpResponse = nlpResponse;
            this.sparqlQuery = sparqlQuery;
        }
    }
}

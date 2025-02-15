package org.gait.service;

import org.apache.jena.query.*;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.QuerySendMode;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.gait.vocabulary.UserHistoryOntology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserHistoryService {

    @Value("${blazegraph.endpoint:http://localhost:9999/blazegraph/namespace/kb/sparql}")
    private String blazegraphEndpoint;

    // Prefixes for our user history ontology and XSD.
    private static final String PREFIXES = "PREFIX uh: <" + UserHistoryOntology.NS + "> " +
            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> ";

    /**
     * Generates a unique URI for a history record using the user ID and prompt.
     */
    public String generateHistoryURI(String userId, String prompt) {
        String base = userId + "_" + prompt;
        return "urn:userhistory:" + URLEncoder.encode(base, StandardCharsets.UTF_8);
    }

    /**
     * Sanitizes input by escaping double quotes and removing newlines.
     */
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /**
     * Saves a user history record in Blazegraph with userId, prompt, and creation timestamp.
     */
    public void saveUserHistory(String userId, String prompt) {
        String historyURI = generateHistoryURI(userId, prompt);
        String safeUserId = sanitize(userId);
        String safePrompt = sanitize(prompt);
        String timestamp = Instant.now().toString(); // ISO-8601 format

        String updateString = PREFIXES +
                "INSERT DATA { " +
                "  <" + historyURI + "> a <" + UserHistoryOntology.UserHistory + "> ; " +
                "    <" + UserHistoryOntology.userId + "> \"" + safeUserId + "\" ; " +
                "    <" + UserHistoryOntology.prompt + "> \"" + safePrompt + "\" ; " +
                "    <" + UserHistoryOntology.createdAt + "> \"" + timestamp + "\"^^xsd:dateTime ." +
                "}";
        UpdateRequest updateRequest = UpdateFactory.create(updateString);
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(updateRequest, blazegraphEndpoint);
        processor.execute();
    }

    /**
     * Retrieves all history records for a given user ID.
     */
    public List<UserHistoryEntry> getHistoryForUser(String userId) {
        String safeUserId = sanitize(userId);
        String queryString = PREFIXES +
                "SELECT ?s ?prompt ?createdAt WHERE { " +
                "  ?s a <" + UserHistoryOntology.UserHistory + "> ; " +
                "     <" + UserHistoryOntology.userId + "> ?uid ; " +
                "     <" + UserHistoryOntology.prompt + "> ?prompt ; " +
                "     <" + UserHistoryOntology.createdAt + "> ?createdAt . " +
                "  FILTER(str(?uid) = \"" + safeUserId + "\")" +
                "}";
        Query query = QueryFactory.create(queryString);
        List<UserHistoryEntry> entries = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionHTTP.newBuilder()
                .endpoint(blazegraphEndpoint)
                .query(query)
                .sendMode(QuerySendMode.asPost)
                .build()) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                String prompt = sol.getLiteral("prompt").getString();
                String createdAt = sol.getLiteral("createdAt").getString();
                entries.add(new UserHistoryEntry(userId, prompt, createdAt));
            }
        }
        return entries;
    }

    /**
     * DTO representing a user history record.
     */
    public static class UserHistoryEntry {
        public final String userId;
        public final String prompt;
        public final String createdAt;

        public UserHistoryEntry(String userId, String prompt, String createdAt) {
            this.userId = userId;
            this.prompt = prompt;
            this.createdAt = createdAt;
        }
    }
}

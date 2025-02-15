package org.gait.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.gait.dto.ClientRequest;
import org.gait.dto.NLPResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final RestTemplate restTemplate;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlazegraphCacheService cacheService;

    /**
     * Processes a client prompt and returns the final GraphQL API result.
     * First checks for a cached result; if none is found, it processes the prompt.
     */
    public String handleClientPrompt(ClientRequest request) {
        String prompt = request.getPrompt();

        // Check if a valid cached entry exists.
        try {
            BlazegraphCacheService.CachedEntry cached = cacheService.fetchCacheEntry(prompt);
            if (cached != null) {
                System.out.println("Cache hit!");
                System.out.println("Cached GraphQL Result: " + cached.graphQLResult);
                return cached.graphQLResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // No cache found: call NLP service.
        String nlpResponse = callNlpService(request);
        System.out.println("Received NLP response: " + nlpResponse);

        // Process the NLP response to build and execute the GraphQL query.
        return processNlpResponse(nlpResponse, prompt);
    }

    /**
     * Simulates a call to an NLP service.
     */
    public String callNlpService(ClientRequest request) {
        String api = request.getApi().toString();
        if ("countries".equalsIgnoreCase(api)) {
            return """
                    {
                      "action": "QUERY",
                      "target": "country",
                      "identifier": "BR",
                      "subEntity": "continent",
                      "limit": 1,
                      "constraints": [],
                      "fields": ["name", "code"],
                      "api": "countries"
                    }""";
        } else {
            return """
                    {
                      "action": "QUERY",
                      "target": "user",
                      "identifier": "octocat",
                      "subEntity": "repositories",
                      "limit": 5,
                      "constraints": ["most starred"],
                      "fields": ["name", "description", "stargazerCount"],
                      "api": "github"
                    }""";
        }
    }

    /**
     * Processes the NLP response by:
     * - Parsing the response.
     * - Loading the appropriate ontology.
     * - Building the SPARQL and GraphQL queries.
     * - Invoking the external GraphQL API.
     * - Caching the final result.
     * Returns the final GraphQL API result.
     */
    public String processNlpResponse(String nlpResponse, String originalPrompt) {
        try {
            NLPResponse response = objectMapper.readValue(nlpResponse, NLPResponse.class);
            String target = response.getTarget();
            String subEntity = response.getSubEntity();
            List<String> constraints = response.getConstraints();
            String constraint = (constraints != null && !constraints.isEmpty()) ? constraints.get(0) : null;

            // Select ontology file based on API.
            String ontologyFile;
            if ("github".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_github.ttl";
            } else if ("countries".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_countries.ttl";
            } else {
                System.err.println("Unknown API: " + response.getApi());
                return "";
            }

            // Load the ontology.
            Resource ontologyResource = resourceLoader.getResource(ontologyFile);
            Model model = ModelFactory.createDefaultModel();
            try (InputStream in = ontologyResource.getInputStream()) {
                model.read(in, null, "TTL");
            } catch (IOException e) {
                System.err.println("Error loading ontology: " + e.getMessage());
                return "";
            }

            // Retrieve target mapping.
            String targetSparql = "PREFIX ex: <http://example.org/ontology#> " +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "SELECT ?targetField ?identifierArgument WHERE { " +
                    "  ?targetConcept rdfs:label \"" + target + "\" ; " +
                    "                 ex:mapsToField ?targetField ; " +
                    "                 ex:identifierArgument ?identifierArgument . " +
                    "}";
            String targetField = "";
            String identifierArgument = "";
            Query targetQuery = QueryFactory.create(targetSparql);
            try (QueryExecution qexec = QueryExecutionFactory.create(targetQuery, model)) {
                ResultSet results = qexec.execSelect();
                if (results.hasNext()) {
                    QuerySolution sol = results.nextSolution();
                    targetField = sol.getLiteral("targetField").getString();
                    identifierArgument = sol.getLiteral("identifierArgument").getString();
                }
            }

            // Retrieve sub-entity mapping.
            String subEntitySparql = "PREFIX ex: <http://example.org/ontology#> " +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "SELECT ?subEntityField ?graphqlType ?argumentField ?orderingField ?defaultDirection WHERE { " +
                    "  ?concept rdfs:label \"" + subEntity + "\" ; " +
                    "           ex:mapsToGraphQLType ?graphqlType ; " +
                    "           ex:mapsToField ?subEntityField . " +
                    (constraint != null ?
                            "  OPTIONAL { " +
                                    "    ?constraintConcept rdfs:label \"" + constraint + "\" ; " +
                                    "                      ex:mapsToArgumentField ?argumentField ; " +
                                    "                      ex:mapsToOrderingField ?orderingField ; " +
                                    "                      ex:defaultDirection ?defaultDirection . " +
                                    "  } " : "") +
                    "}";
            Query subEntityQuery = QueryFactory.create(subEntitySparql);
            String subEntityField = "";
            String orderingField = "";
            String defaultDirection = "";
            String argumentField = "";
            try (QueryExecution qexec = QueryExecutionFactory.create(subEntityQuery, model)) {
                ResultSet results = qexec.execSelect();
                if (results.hasNext()) {
                    QuerySolution sol = results.nextSolution();
                    subEntityField = sol.getLiteral("subEntityField").getString();
                    if (sol.contains("argumentField"))
                        argumentField = sol.getLiteral("argumentField").getString();
                    if (sol.contains("orderingField"))
                        orderingField = sol.getLiteral("orderingField").getString();
                    if (sol.contains("defaultDirection"))
                        defaultDirection = sol.getLiteral("defaultDirection").getString();
                }
            }

            // Build the GraphQL query.
            String graphQLQuery = buildGraphQLQuery(response, targetField, identifierArgument,
                    subEntityField, argumentField, orderingField, defaultDirection);
            System.out.println("Generated GraphQL Query:");
            System.out.println(graphQLQuery);

            // Call the external GraphQL API.
            String graphQLResult = queryExternalGraphQLApi(graphQLQuery, response.getApi());
            System.out.println("GraphQL API response:");
            System.out.println(graphQLResult);

            // Cache the final result (storing only the prompt and final GraphQL result).
            cacheService.saveCacheEntry(originalPrompt, graphQLResult);

            return graphQLResult;
        } catch (IOException e) {
            System.err.println("Error parsing NLP response: " + e.getMessage());
            return "";
        } catch (Exception ex) {
            System.err.println("Processing error: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Builds a GraphQL query based on the NLP response and the retrieved mappings.
     */
    public String buildGraphQLQuery(NLPResponse response,
                                    String targetField,
                                    String identifierArgument,
                                    String subEntityField,
                                    String argumentField,
                                    String orderingField,
                                    String defaultDirection) {
        if ("countries".equalsIgnoreCase(response.getApi())) {
            StringBuilder sb = new StringBuilder();
            sb.append("query {\n");
            sb.append("  ").append(targetField).append("(").append(identifierArgument)
                    .append(": \"").append(response.getIdentifier()).append("\") {\n");
            sb.append("    ").append(subEntityField).append(" {\n");
            for (String field : response.getFields()) {
                sb.append("      ").append(field).append("\n");
            }
            sb.append("    }\n");
            sb.append("  }\n");
            sb.append("}\n");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("query {\n");
            sb.append("  ").append(targetField).append("(").append(identifierArgument)
                    .append(": \"").append(response.getIdentifier()).append("\") {\n");
            sb.append("    ").append(subEntityField).append("(first: ").append(response.getLimit());
            if (!argumentField.isEmpty() && !orderingField.isEmpty() && !defaultDirection.isEmpty()) {
                sb.append(", ").append(argumentField)
                        .append(": { field: ").append(orderingField)
                        .append(", direction: ").append(defaultDirection).append(" }");
            }
            sb.append(") {\n");
            sb.append("      nodes {\n");
            for (String field : response.getFields()) {
                sb.append("        ").append(field).append("\n");
            }
            sb.append("      }\n");
            sb.append("    }\n");
            sb.append("  }\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    /**
     * Executes the GraphQL API call to an external endpoint and returns the result.
     */
    public String queryExternalGraphQLApi(String graphQLQuery, String api) {
        String publicEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if ("github".equalsIgnoreCase(api)) {
            publicEndpoint = "https://api.github.com/graphql";
            headers.set("Authorization", "Bearer YOUR_GITHUB_TOKEN");
        } else if ("countries".equalsIgnoreCase(api)) {
            publicEndpoint = "https://countries.trevorblades.com/";
        } else {
            System.err.println("Unknown API: " + api);
            return "";
        }

        Map<String, String> body = new HashMap<>();
        body.put("query", graphQLQuery);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(publicEndpoint, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            System.err.println("Error querying GraphQL API (" + api + "): " + response.getStatusCode());
            return "";
        }
    }
}

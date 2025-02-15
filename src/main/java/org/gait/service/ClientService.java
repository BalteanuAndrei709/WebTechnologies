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
    private final BlazegraphCacheService blazegraphCacheService;

    public void createPrompt(ClientRequest request) {
        String nlpResponse = callNlpService(request);
        System.out.println("Received NLP response: " + nlpResponse);
        processNlpResponse(nlpResponse, request.getPrompt());
    }

    /**
     * Simulates calling an NLP service.
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
     * Processes the NLP response. It first checks the cache; if there's a cache hit, it uses the cached data.
     * Otherwise, it processes the prompt, generates the SPARQL query, caches the result, and calls the public API.
     */
    public void processNlpResponse(String nlpResponse, String originalPrompt) {
        try {
            // Check cache first.
            BlazegraphCacheService.CachedEntry cachedEntry = blazegraphCacheService.getCachedEntry(originalPrompt);
            if (cachedEntry != null) {
                System.out.println("Cache hit! Using cached data.");
                System.out.println("Cached NLP Response: " + cachedEntry.nlpResponse);
                System.out.println("Cached SPARQL Query: " + cachedEntry.sparqlQuery);
                queryPublicGraphQLApi(cachedEntry.sparqlQuery, parseApiFromResponse(nlpResponse));
                return;
            }

            // No cache; process normally.
            NLPResponse response = objectMapper.readValue(nlpResponse, NLPResponse.class);
            String target = response.getTarget();
            String subEntity = response.getSubEntity();
            List<String> constraints = response.getConstraints();
            String constraint = (constraints != null && !constraints.isEmpty()) ? constraints.get(0) : null;

            String ontologyFile;
            if ("github".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_github.ttl";
            } else if ("countries".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_countries.ttl";
            } else {
                System.err.println("Unknown API: " + response.getApi());
                return;
            }

            Resource ontologyResource = resourceLoader.getResource(ontologyFile);
            Model model = ModelFactory.createDefaultModel();
            try (InputStream in = ontologyResource.getInputStream()) {
                model.read(in, null, "TTL");
            } catch (IOException e) {
                System.err.println("Error loading ontology: " + e.getMessage());
                return;
            }

            // Query target mapping.
            String targetSparqlQuery = "PREFIX ex: <http://example.org/ontology#> " +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "SELECT ?targetField ?identifierArgument WHERE { " +
                    "  ?targetConcept rdfs:label \"" + target + "\" ; " +
                    "                 ex:mapsToField ?targetField ; " +
                    "                 ex:identifierArgument ?identifierArgument . " +
                    "}";
            String targetField = "";
            String identifierArgument = "";
            Query targetQuery = QueryFactory.create(targetSparqlQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(targetQuery, model)) {
                ResultSet targetResults = qexec.execSelect();
                if (targetResults.hasNext()) {
                    QuerySolution sol = targetResults.nextSolution();
                    targetField = sol.getLiteral("targetField").getString();
                    identifierArgument = sol.getLiteral("identifierArgument").getString();
                }
            }

            // Query sub-entity mapping.
            String sparqlQuery = "PREFIX ex: <http://example.org/ontology#> " +
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
            Query query = QueryFactory.create(sparqlQuery);
            String subEntityField = "";
            String orderingField = "";
            String defaultDirection = "";
            String argumentField = "";
            try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
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

            String graphQLQuery = buildGraphQLQuery(response, targetField, identifierArgument,
                    subEntityField, argumentField, orderingField, defaultDirection);
            System.out.println("Generated GraphQL Query:");
            System.out.println(graphQLQuery);

            // Cache the result.
            blazegraphCacheService.cachePrompt(originalPrompt, nlpResponse, graphQLQuery);
            queryPublicGraphQLApi(graphQLQuery, response.getApi());

        } catch (IOException e) {
            System.err.println("Error parsing NLP response: " + e.getMessage());
        } catch (Exception ex) {
            System.err.println("Processing error: " + ex.getMessage());
        }
    }

    public String buildGraphQLQuery(NLPResponse response,
                                    String targetField,
                                    String identifierArgument,
                                    String subEntityField,
                                    String argumentField,
                                    String orderingField,
                                    String defaultDirection) {
        if ("countries".equalsIgnoreCase(response.getApi())) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("query {\n");
            queryBuilder.append("  ").append(targetField)
                    .append("(").append(identifierArgument)
                    .append(": \"").append(response.getIdentifier()).append("\") {\n");
            queryBuilder.append("    ").append(subEntityField).append(" {\n");
            for (String field : response.getFields()) {
                queryBuilder.append("      ").append(field).append("\n");
            }
            queryBuilder.append("    }\n");
            queryBuilder.append("  }\n");
            queryBuilder.append("}\n");
            return queryBuilder.toString();
        } else {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("query {\n");
            queryBuilder.append("  ").append(targetField)
                    .append("(").append(identifierArgument)
                    .append(": \"").append(response.getIdentifier()).append("\") {\n");
            queryBuilder.append("    ").append(subEntityField)
                    .append("(first: ").append(response.getLimit());
            if (!argumentField.isEmpty() && !orderingField.isEmpty() && !defaultDirection.isEmpty()) {
                queryBuilder.append(", ").append(argumentField)
                        .append(": { field: ").append(orderingField)
                        .append(", direction: ").append(defaultDirection).append(" }");
            }
            queryBuilder.append(") {\n");
            queryBuilder.append("      nodes {\n");
            for (String field : response.getFields()) {
                queryBuilder.append("        ").append(field).append("\n");
            }
            queryBuilder.append("      }\n");
            queryBuilder.append("    }\n");
            queryBuilder.append("  }\n");
            queryBuilder.append("}\n");
            return queryBuilder.toString();
        }
    }

    public void queryPublicGraphQLApi(String graphQLQuery, String api) {
        String publicGraphQLEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if ("github".equalsIgnoreCase(api)) {
            publicGraphQLEndpoint = "https://api.github.com/graphql";
            headers.set("Authorization", "Bearer test123");
        } else if ("countries".equalsIgnoreCase(api)) {
            publicGraphQLEndpoint = "https://countries.trevorblades.com/";
        } else {
            System.err.println("Unknown API: " + api);
            return;
        }

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", graphQLQuery);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(publicGraphQLEndpoint, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("GraphQL API response:");
            System.out.println(response.getBody());
        } else {
            System.err.println("Error querying GraphQL API (" + api + "): " + response.getStatusCode());
        }
    }

    private String parseApiFromResponse(String nlpResponse) {
        try {
            NLPResponse response = objectMapper.readValue(nlpResponse, NLPResponse.class);
            return response.getApi();
        } catch (Exception e) {
            return "";
        }
    }
}

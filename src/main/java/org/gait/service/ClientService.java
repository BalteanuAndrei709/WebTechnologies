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
    private final ObjectMapper objectMapper = new ObjectMapper(); // For JSON parsing

    public void createPrompt(ClientRequest request) {
        // 1. Call the NLP service to extract key elements from the prompt.
        String nlpResponse = callNlpService(request);
        System.out.println("Received NLP response: " + nlpResponse);

        // 2. Process the NLP response: perform SPARQL queries against the appropriate ontology,
        //    build the GraphQL query, and query the public API.
        processNlpResponse(nlpResponse);
    }

    /**
     * Simulates sending the ClientRequest to an external NLP service.
     * Returns a JSON string with the extracted key elements.
     * Here, we choose the response based on a property in ClientRequest.
     */
    public String callNlpService(ClientRequest request) {
        // For demonstration, assume ClientRequest has a field "api" (either "github" or "countries")
        String api = request.getApi().toString();
        if ("countries".equalsIgnoreCase(api)) {
            // Simulated NLP response for Countries API
            return "{\n" +
                    "  \"action\": \"QUERY\",\n" +
                    "  \"target\": \"country\",\n" +
                    "  \"identifier\": \"BR\",\n" +
                    "  \"subEntity\": \"continent\",\n" +
                    "  \"limit\": 1,\n" +
                    "  \"constraints\": [],\n" +
                    "  \"fields\": [\"name\", \"code\"],\n" +
                    "  \"api\": \"countries\"\n" +
                    "}";
        } else {
            // Default to GitHub response
            return "{\n" +
                    "  \"action\": \"QUERY\",\n" +
                    "  \"target\": \"user\",\n" +
                    "  \"identifier\": \"octocat\",\n" +
                    "  \"subEntity\": \"repositories\",\n" +
                    "  \"limit\": 5,\n" +
                    "  \"constraints\": [\"most starred\"],\n" +
                    "  \"fields\": [\"name\", \"description\", \"stargazerCount\"],\n" +
                    "  \"api\": \"github\"\n" +
                    "}";
        }
    }

    /**
     * Processes the NLP service response by:
     * - Parsing the JSON.
     * - Loading the appropriate ontology file based on the 'api' field.
     * - Running SPARQL queries to dynamically resolve both the target mapping and the sub-entity mapping.
     * - Building the GraphQL query string.
     * - Querying the public GraphQL API with the generated query.
     */
    public void processNlpResponse(String nlpResponse) {
        try {
            // Parse the NLP JSON into an NLPResponse DTO.
            NLPResponse response = objectMapper.readValue(nlpResponse, NLPResponse.class);

            // Retrieve the target and sub-entity from the NLP response.
            String target = response.getTarget();
            String subEntity = response.getSubEntity();
            List<String> constraints = response.getConstraints();
            String constraint = (constraints != null && !constraints.isEmpty()) ? constraints.get(0) : null;

            // Determine which ontology file to load based on the API.
            String ontologyFile;
            if ("github".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_github.ttl";
            } else if ("countries".equalsIgnoreCase(response.getApi())) {
                ontologyFile = "classpath:ontology/graphQLOntology_countries.ttl";
            } else {
                System.err.println("Unknown API: " + response.getApi());
                return;
            }

            // Load the ontology from the classpath.
            Resource ontologyResource = resourceLoader.getResource(ontologyFile);
            Model model = ModelFactory.createDefaultModel();
            try (InputStream in = ontologyResource.getInputStream()) {
                model.read(in, null, "TTL");
            } catch (IOException e) {
                System.err.println("Error loading ontology: " + e.getMessage());
                return;
            }

            // --- Query for the target mapping ---
            String targetSparqlQuery = "PREFIX ex: <http://example.org/ontology#> " +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "SELECT ?targetField ?identifierArgument " +
                    "WHERE { " +
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

            // --- Query for the sub-entity mapping and constraint ---
            String sparqlQuery = "PREFIX ex: <http://example.org/ontology#> " +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "SELECT ?subEntityField ?graphqlType ?argumentField ?orderingField ?defaultDirection " +
                    "WHERE { " +
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

            // Generate the GraphQL query using both target and sub-entity mappings.
            String graphQLQuery = buildGraphQLQuery(response, targetField, identifierArgument,
                    subEntityField, argumentField, orderingField, defaultDirection);
            System.out.println("Generated GraphQL Query:");
            System.out.println(graphQLQuery);

            // Query the public GraphQL API using the generated query.
            queryPublicGraphQLApi(graphQLQuery, response.getApi());

        } catch (IOException e) {
            System.err.println("Error parsing NLP response: " + e.getMessage());
        }
    }

    /**
     * Queries the appropriate public GraphQL API using the provided GraphQL query.
     * Chooses the endpoint (and headers, if necessary) based on the API.
     */
    public void queryPublicGraphQLApi(String graphQLQuery, String api) {
        String publicGraphQLEndpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if ("github".equalsIgnoreCase(api)) {
            publicGraphQLEndpoint = "https://api.github.com/graphql";
            // For GitHub, set your authorization token here.
            headers.set("Authorization", "Bearer my_git_token");
        } else if ("countries".equalsIgnoreCase(api)) {
            publicGraphQLEndpoint = "https://countries.trevorblades.com/";
            // No authorization needed for the Countries API.
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
    /**
     * Constructs a GraphQL query string using the extracted NLP response and the mappings from the ontology.
     * Adjusts the query format based on the API (e.g., "countries" vs. "github").
     */
    public String buildGraphQLQuery(NLPResponse response,
                                    String targetField,
                                    String identifierArgument,
                                    String subEntityField,
                                    String argumentField,
                                    String orderingField,
                                    String defaultDirection) {
        // If the API is "countries", generate a simple nested query.
        if ("countries".equalsIgnoreCase(response.getApi())) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("query {\n");
            queryBuilder.append("  ").append(targetField)
                    .append("(").append(identifierArgument)
                    .append(": \"").append(response.getIdentifier()).append("\") {\n");
            // For the Countries API, we assume the sub-entity (e.g., "continent") is a simple object field.
            queryBuilder.append("    ").append(subEntityField).append(" {\n");
            for (String field : response.getFields()) {
                queryBuilder.append("      ").append(field).append("\n");
            }
            queryBuilder.append("    }\n");
            queryBuilder.append("  }\n");
            queryBuilder.append("}\n");
            return queryBuilder.toString();
        }
        // Otherwise, for APIs like GitHub, use the connection pattern.
        else {
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

}

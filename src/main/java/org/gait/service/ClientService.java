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

        // 2. Process the NLP response: perform SPARQL queries against the local ontology,
        //    build the GraphQL query and query the public API.
        processNlpResponse(nlpResponse);
    }

    /**
     * Simulates sending the ClientRequest to an external NLP service.
     * Returns a JSON string with the extracted key elements.
     */
    public String callNlpService(ClientRequest request) {
        // Simulated NLP service response that extracts key elements from the prompt.
        String simulatedResponse = "{\n" +
                "  \"action\": \"QUERY\",\n" +
                "  \"target\": \"user\",\n" +
                "  \"identifier\": \"octocat\",\n" +
                "  \"subEntity\": \"repositories\",\n" +
                "  \"limit\": 5,\n" +
                "  \"constraints\": [\"most starred\"],\n" +
                "  \"fields\": [\"name\", \"description\", \"stargazerCount\"]\n" +
                "}";
        return simulatedResponse;
    }

    /**
     * Processes the NLP service response by:
     * - Parsing the JSON.
     * - Loading the local ontology.
     * - Running SPARQL queries to dynamically resolve both the target mapping and the sub-entity mapping.
     * - Building the GraphQL query string.
     * - Querying the public GraphQL API with the generated query.
     */
    public void processNlpResponse(String nlpResponse) {
        try {
            // Parse the NLP JSON into an NLPResponse DTO.
            NLPResponse response = objectMapper.readValue(nlpResponse, NLPResponse.class);

            // Retrieve the target (e.g., "user") and sub-entity (e.g., "repositories") from the NLP response.
            String target = response.getTarget();
            String subEntity = response.getSubEntity();
            List<String> constraints = response.getConstraints();
            String constraint = (constraints != null && !constraints.isEmpty()) ? constraints.get(0) : null;

            // Load the ontology from the classpath (located at src/main/resources/ontology/graphQLOntology.ttl)
            Resource ontologyResource = resourceLoader.getResource("classpath:ontology/graphQLOntology.ttl");
            Model model = ModelFactory.createDefaultModel();
            try (InputStream in = ontologyResource.getInputStream()) {
                model.read(in, null, "TTL");
            } catch (IOException e) {
                System.err.println("Error loading ontology: " + e.getMessage());
                return;
            }

            // --- Query for the target mapping (e.g., "user") ---
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

            // --- Query for the sub-entity mapping (e.g., "repositories") and constraint (e.g., "most starred") ---
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

            // --- Query the public GraphQL API using the generated query ---
            queryPublicGraphQLApi(graphQLQuery);

        } catch (IOException e) {
            System.err.println("Error parsing NLP response: " + e.getMessage());
        }
    }

    /**
     * Constructs a GraphQL query string using the extracted NLP response and the mappings from the ontology.
     */
    public String buildGraphQLQuery(NLPResponse response,
                                    String targetField,
                                    String identifierArgument,
                                    String subEntityField,
                                    String argumentField,
                                    String orderingField,
                                    String defaultDirection) {
        // Construct a GraphQL query string.
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

    /**
     * Queries a public GraphQL API using the provided GraphQL query.
     * For example, this method posts the query to the API endpoint and prints the response.
     */
    public void queryPublicGraphQLApi(String graphQLQuery) {
        // Replace with your public GraphQL API endpoint.
        // For demonstration, we'll use GitHub's GraphQL API endpoint.
        String publicGraphQLEndpoint = "https://api.github.com/graphql";

        // Create a JSON body with the query.
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", graphQLQuery);

        // Set headers. For GitHub's API, you need to provide an Authorization token.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Uncomment and replace with your GitHub token if needed:
         headers.set("Authorization", "Bearer token");

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        // Send the POST request.
        ResponseEntity<String> response = restTemplate.postForEntity(publicGraphQLEndpoint, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("GraphQL API response:");
            System.out.println(response.getBody());
        } else {
            System.err.println("Error querying GraphQL API: " + response.getStatusCode());
        }
    }
}

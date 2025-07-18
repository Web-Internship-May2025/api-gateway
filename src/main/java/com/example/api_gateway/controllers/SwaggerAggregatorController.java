package com.example.api_gateway.controllers;

import com.example.api_gateway.swaggerServices.SwaggerServicesConfig;
import com.example.api_gateway.swaggerServices.SwaggerServicesConfig.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/v3/api-docs")
public class SwaggerAggregatorController {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SwaggerServicesConfig cfg;

    private final Map<String, String> servicePathPrefixMap = Map.of(
            "car-service", "/cars",
            "payment-service", "/payments",
            "user-management-service", "/users",
            "policy-service", "/policies",
            "reporting-service", "/reporting",
            "claims-management-service", "/claims"
    );

    public SwaggerAggregatorController(SwaggerServicesConfig cfg) {
        this.cfg = cfg;
    }

    private void prefixPaths(ObjectNode swagger, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ObjectNode pathsNode = (ObjectNode) swagger.get("paths");
        if (pathsNode == null) {
            return;
        }
        ObjectNode prefixedPaths = mapper.createObjectNode();
        pathsNode.fields().forEachRemaining(entry -> {
            String originalPath = entry.getKey();
            prefixedPaths.set(prefix + originalPath, entry.getValue());
        });
        swagger.set("paths", prefixedPaths);
    }

    private void createServersNode(ObjectNode swagger) {
        ArrayNode servers = mapper.createArrayNode();
        ObjectNode node = mapper.createObjectNode();
        node.put("url", "http://localhost:8080");
        node.put("description", "API Gateway");
        servers.add(node);
        swagger.set("servers", servers);
    }

    private void addSecurityDefinitions(ObjectNode swagger) {
        ObjectNode componentsNode = (ObjectNode) swagger.get("components");
        if (componentsNode == null) {
            componentsNode = mapper.createObjectNode();
            swagger.set("components", componentsNode);
        }

        ObjectNode securitySchemes = mapper.createObjectNode();
        ObjectNode bearerScheme = mapper.createObjectNode();
        bearerScheme.put("type", "http");
        bearerScheme.put("scheme", "bearer");
        bearerScheme.put("bearerFormat", "JWT");
        securitySchemes.set("BearerAuth", bearerScheme);
        componentsNode.set("securitySchemes", securitySchemes);

        ArrayNode securityArray = mapper.createArrayNode();
        ObjectNode securityRequirement = mapper.createObjectNode();
        securityRequirement.set("BearerAuth", mapper.createArrayNode()); // Nema scopes
        securityArray.add(securityRequirement);
        swagger.set("security", securityArray);
    }

    // Endpoint za objedinjenu dokumentaciju
    @GetMapping(value = "/combined", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> combined() {
        ObjectNode combined = mapper.createObjectNode();
        combined.put("openapi", "3.0.1");

        ObjectNode info = mapper.createObjectNode();
        info.put("title", "Aggregated API");
        info.put("version", "1.0.0");
        combined.set("info", info);

        ObjectNode paths = mapper.createObjectNode();
        ObjectNode components = mapper.createObjectNode();

        cfg.getServices().forEach(svc -> {
            try {
                ObjectNode swagger = rest.getForObject(svc.getUrl(), ObjectNode.class);
                if (swagger == null) return;

                String prefix = servicePathPrefixMap.getOrDefault(svc.getName(), "");
                prefixPaths(swagger, prefix);

                ObjectNode pathsNode = (ObjectNode) swagger.get("paths");
                if (pathsNode != null) {
                    pathsNode.fields().forEachRemaining(e -> paths.set(e.getKey(), e.getValue()));
                }
            } catch (Exception ex) {
                System.err.println("Failed to fetch swagger from " + svc.getUrl());
            }
        });

        combined.set("paths", paths);
        if (components.has("schemas")) combined.set("components", components);

        createServersNode(combined);
        addSecurityDefinitions(combined);

        return ResponseEntity.ok(combined);
    }

    // Endpoint za pojedinačnu Swagger definiciju
    @GetMapping(value = "/{serviceName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> single(@PathVariable String serviceName) {
        Service svc = cfg.getServices().stream()
                .filter(s -> s.getName().equalsIgnoreCase(serviceName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown service: " + serviceName
                ));

        ObjectNode swagger = rest.getForObject(svc.getUrl(), ObjectNode.class);
        if (swagger == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String prefix = servicePathPrefixMap.getOrDefault(svc.getName(), "");
        prefixPaths(swagger, prefix);

        createServersNode(swagger);
        addSecurityDefinitions(swagger);

        return ResponseEntity.ok(swagger);
    }
}
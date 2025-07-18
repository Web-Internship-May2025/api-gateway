package com.example.api_gateway.swaggerServices;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "swagger")
public class SwaggerServicesConfig {

    public static class Service {
        private String name;
        private String url;

        public Service() { }

        public Service(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    private List<Service> services;
    public SwaggerServicesConfig() { }

    public SwaggerServicesConfig(List<Service> services) {
        this.services = services;
    }
    public List<Service> getServices() { return services; }
    public void setServices(List<Service> services) { this.services = services; }
}
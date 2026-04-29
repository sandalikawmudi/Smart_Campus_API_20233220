package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * Part 1 - Discovery Endpoint
 * GET /api/v1  -> Returns API metadata, version, contact, and resource links (HATEOAS)
 */
@Path("")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover(@Context UriInfo uriInfo) {
        String baseUri = uriInfo.getBaseUri().toString().replaceAll("/$", "");

        Map<String, Object> response = new HashMap<>();
        response.put("api", "Smart Campus Sensor & Room Management API");
        response.put("version", "1.0");
        response.put("description", "RESTful API for managing campus rooms and IoT sensors");
        response.put("contact", "admin@smartcampus.westminster.ac.uk");
        response.put("status", "operational");

        // HATEOAS - links to primary resource collections
        Map<String, String> links = new HashMap<>();
        links.put("self",    baseUri);
        links.put("rooms",   baseUri + "/rooms");
        links.put("sensors", baseUri + "/sensors");
        response.put("_links", links);

        return Response.ok(response).build();
    }
}

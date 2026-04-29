package com.smartcampus.exception.mapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger logger = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        // Preserve framework/client HTTP exceptions (e.g., 404, 405, 415) instead of masking them as 500.
        if (exception instanceof WebApplicationException webApplicationException) {
            int status = webApplicationException.getResponse() == null
                    ? Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                    : webApplicationException.getResponse().getStatus();

            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "status", status,
                            "error", "Request Handling Error",
                            "message", webApplicationException.getMessage() == null
                                    ? "The request could not be processed."
                                    : webApplicationException.getMessage()
                    ))
                    .build();
        }

        logger.log(Level.SEVERE, "Unhandled exception: " + exception.getMessage(), exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status",  500,
                    "error",   "Internal Server Error",
                    "message", "An unexpected error occurred. Please contact the administrator.",
                    "hint",    "Check server logs for details."
                )).build();
    }
}

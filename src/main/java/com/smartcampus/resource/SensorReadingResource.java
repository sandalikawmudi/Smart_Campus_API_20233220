package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Part 4 - Sub-Resource for Sensor Readings
 * Accessed via: /api/v1/sensors/{sensorId}/readings
 * Instantiated by SensorResource's sub-resource locator.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;
    private final Map<String, Sensor> sensors = DataStore.getSensors();
    private final Map<String, List<SensorReading>> readings = DataStore.getReadings();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId == null ? "" : sensorId.trim();
    }

    // ----------------------------------------------------------------
    // GET /api/v1/sensors/{sensorId}/readings  -> Fetch all readings
    // ----------------------------------------------------------------
    @GET
    public Response getReadings() {
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor not found: " + sensorId))
                    .build();
        }

        List<SensorReading> sensorReadings = readings.getOrDefault(sensorId, new ArrayList<>());
        return Response.ok(sensorReadings).build();
    }

    // ----------------------------------------------------------------
    // POST /api/v1/sensors/{sensorId}/readings  -> Append a new reading
    // Side Effect: Updates sensor's currentValue to the new reading value
    // Throws 403 if sensor is in MAINTENANCE status
    // ----------------------------------------------------------------
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor not found: " + sensorId))
                    .build();
        }
        if (reading == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Reading payload is required"))
                    .build();
        }
        if (Double.isNaN(reading.getValue()) || Double.isInfinite(reading.getValue())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Reading value must be a finite number"))
                    .build();
        }

        // State constraint: MAINTENANCE sensors cannot accept new readings
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                "Sensor '" + sensorId + "' is currently under MAINTENANCE and cannot accept readings."
            );
        }

        if ("OFFLINE".equalsIgnoreCase(sensor.getStatus())) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' is OFFLINE."))
                    .build();
        }

        // Generate ID and timestamp if not provided by client
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        // Save the reading
        readings.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(reading);

        // Side effect: update the parent sensor's currentValue for data consistency
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}

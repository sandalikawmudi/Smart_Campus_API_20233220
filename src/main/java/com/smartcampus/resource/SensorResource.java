package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Part 3 - Sensor Operations
 * Handles /api/v1/sensors and delegates to SensorReadingResource via sub-resource locator
 */
@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final Map<String, Sensor> sensors = DataStore.getSensors();
    private final Map<String, Room> rooms = DataStore.getRooms();
    private final Map<String, List<SensorReading>> readings = DataStore.getReadings();

    // ----------------------------------------------------------------
    // GET /api/v1/sensors          -> List all sensors
    // GET /api/v1/sensors?type=CO2 -> Filter by type
    // ----------------------------------------------------------------
    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        List<Sensor> sensorList = new ArrayList<>(sensors.values());

        if (type != null && !type.isBlank()) {
            String normalizedType = type.trim();
            sensorList = sensorList.stream()
                    .filter(s -> s.getType().equalsIgnoreCase(normalizedType))
                    .collect(Collectors.toList());
        }

        return Response.ok(sensorList).build();
    }

    // ----------------------------------------------------------------
    // POST /api/v1/sensors  -> Register a new sensor
    // Validates that the referenced roomId exists
    // ----------------------------------------------------------------
    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Sensor ID is required"))
                    .build();
        }
        if (sensor.getType() == null || sensor.getType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Sensor type is required"))
                    .build();
        }
        if (sensor.getRoomId() == null || sensor.getRoomId().isBlank()) {
            throw new LinkedResourceNotFoundException(
                    "Cannot create sensor: roomId is required and must reference an existing room."
            );
        }

        sensor.setId(sensor.getId().trim());
        sensor.setType(sensor.getType().trim());
        sensor.setRoomId(sensor.getRoomId().trim());

        if (sensors.containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Sensor with ID '" + sensor.getId() + "' already exists"))
                    .build();
        }

        // Validate that roomId exists - throws 422 if not found
        if (!rooms.containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                "Cannot create sensor: roomId '" + sensor.getRoomId() + "' does not exist in the system."
            );
        }

        // Default status to ACTIVE if not provided
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        } else {
            String normalizedStatus = sensor.getStatus().trim().toUpperCase();
            if (!List.of("ACTIVE", "MAINTENANCE", "OFFLINE").contains(normalizedStatus)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Sensor status must be ACTIVE, MAINTENANCE, or OFFLINE"))
                        .build();
            }
            sensor.setStatus(normalizedStatus);
        }

        sensors.put(sensor.getId(), sensor);

        // Link sensor to the room
        Room targetRoom = rooms.get(sensor.getRoomId());
        if (targetRoom.getSensorIds() == null) {
            targetRoom.setSensorIds(new CopyOnWriteArrayList<>());
        } else if (!(targetRoom.getSensorIds() instanceof CopyOnWriteArrayList)) {
            targetRoom.setSensorIds(new CopyOnWriteArrayList<>(targetRoom.getSensorIds()));
        }
        if (!targetRoom.getSensorIds().contains(sensor.getId())) {
            targetRoom.getSensorIds().add(sensor.getId());
        }

        // Initialise empty readings list for this sensor
        readings.put(sensor.getId(), new CopyOnWriteArrayList<>());

        return Response.status(Response.Status.CREATED).entity(sensor).build();
    }

    // ----------------------------------------------------------------
    // GET /api/v1/sensors/{sensorId}  -> Get a specific sensor
    // ----------------------------------------------------------------
    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        sensorId = sensorId.trim();
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor not found: " + sensorId))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    // ----------------------------------------------------------------
    // Sub-Resource Locator  ->  /api/v1/sensors/{sensorId}/readings
    // Delegates to SensorReadingResource
    // ----------------------------------------------------------------
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId.trim());
    }
}

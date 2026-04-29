package com.smartcampus.resource;

import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
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
 * Part 2 - Room Management
 * Handles all CRUD operations under /api/v1/rooms
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final Map<String, Room> rooms = DataStore.getRooms();
    private final Map<String, Sensor> sensors = DataStore.getSensors();

    // ----------------------------------------------------------------
    // GET /api/v1/rooms  -> List all rooms
    // ----------------------------------------------------------------
    @GET
    public Response getAllRooms() {
        List<Room> roomList = new ArrayList<>(rooms.values());
        return Response.ok(roomList).build();
    }

    // ----------------------------------------------------------------
    // POST /api/v1/rooms  -> Create a new room
    // ----------------------------------------------------------------
    @POST
    public Response createRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room ID is required"))
                    .build();
        }
        if (room.getName() == null || room.getName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room name is required"))
                    .build();
        }
        if (room.getCapacity() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room capacity must be greater than zero"))
                    .build();
        }
        room.setId(room.getId().trim());
        room.setName(room.getName().trim());

        if (rooms.containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Room with ID '" + room.getId() + "' already exists"))
                    .build();
        }

        // sensorIds are server-managed through sensor registration
        room.setSensorIds(new CopyOnWriteArrayList<>());
        rooms.put(room.getId(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    // ----------------------------------------------------------------
    // GET /api/v1/rooms/{roomId}  -> Get a specific room
    // ----------------------------------------------------------------
    @GET
    @Path("/{roomId}")
    public Response getRoomById(@PathParam("roomId") String roomId) {
        String normalizedRoomId = roomId.trim();
        Room room = rooms.get(normalizedRoomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found: " + normalizedRoomId))
                    .build();
        }
        return Response.ok(room).build();
    }

    // ----------------------------------------------------------------
    // DELETE /api/v1/rooms/{roomId}  -> Delete a room (if no sensors)
    // ----------------------------------------------------------------
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        String normalizedRoomId = roomId.trim();
        Room room = rooms.get(normalizedRoomId);

        // 404 if room doesn't exist (idempotency note: first call 404s gracefully)
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found: " + normalizedRoomId))
                    .build();
        }

        // Business rule: cannot delete a room that still has sensors linked to it.
        List<String> assignedSensors = sensors.values().stream()
                .filter(sensor -> normalizedRoomId.equals(sensor.getRoomId()))
                .map(Sensor::getId)
                .sorted()
                .collect(Collectors.toList());

        if (!assignedSensors.isEmpty()) {
            throw new RoomNotEmptyException(
                "Room '" + normalizedRoomId + "' cannot be deleted. It still has " +
                assignedSensors.size() + " sensor(s) assigned: " + assignedSensors
            );
        }

        rooms.remove(normalizedRoomId);
        return Response.ok(Map.of(
            "message", "Room '" + normalizedRoomId + "' has been successfully decommissioned"
        )).build();
    }
}

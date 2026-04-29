# Smart Campus Sensor & Room Management API

> **Module:** 5COSC022W — Client-Server Architectures
> **Topic:** REST API Design, Development & Implementation

---

## 1. Project Overview

A fully RESTful backend for a university **Smart Campus** initiative — designed to manage campus rooms and their IoT sensors (CO₂ monitors, temperature probes, occupancy trackers, and more).

The API is built on **JAX-RS (Jersey 2.35)**, deployed on **Apache Tomcat 9**, and packaged as a Maven WAR. There is no database — all data lives in-memory using thread-safe `ConcurrentHashMap` and `CopyOnWriteArrayList` collections, which persist for the lifetime of the application.

**Key capabilities:**

- Create, retrieve, and decommission campus rooms
- Register sensors and link them to rooms with referential integrity validation
- Post sensor readings with automatic `currentValue` updates on the parent sensor
- Filter sensors by type via query parameters
- HATEOAS-compliant discovery endpoint for self-documenting navigation
- Global exception mapping with safe, structured error responses (no stack trace leakage)
- Cross-cutting request/response logging via a JAX-RS container filter

---

## 2. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 (compiler) / 21+ (runtime) |
| REST Framework | JAX-RS — Jersey (Servlet container) | 2.35 |
| JSON Binding | Jackson (via `jersey-media-json-jackson`) | bundled with Jersey 2.35 |
| DI Container | Jersey HK2 | 2.35 |
| Application Server | Apache Tomcat | 9.x |
| Build Tool | Apache Maven (WAR packaging) | 3.8+ |
| Servlet API | `javax.servlet-api` (provided scope) | 4.0.1 |

---

## 3. Project Structure

```
smart-campus-api/
│
├── pom.xml
├── nb-configuration.xml
├── scripts/
│   ├── build-war-no-maven.ps1
│   ├── redeploy-tomcat-no-maven.ps1
│   └── tomcat9-clean-deploy.ps1
│
└── src/main/
    ├── java/com/smartcampus/
    │   │
    │   ├── application/
    │   │   └── SmartCampusApplication.java       ← JAX-RS Application entry point (@ApplicationPath)
    │   │
    │   ├── model/
    │   │   ├── Room.java                          ← id, name, capacity, sensorIds
    │   │   ├── Sensor.java                        ← id, type, status, currentValue, roomId
    │   │   └── SensorReading.java                 ← id (UUID), timestamp (epoch ms), value
    │   │
    │   ├── resource/
    │   │   ├── DiscoveryResource.java             ← GET /api/v1  (HATEOAS entry point)
    │   │   ├── RoomResource.java                  ← /api/v1/rooms
    │   │   ├── SensorResource.java                ← /api/v1/sensors  (also sub-resource locator)
    │   │   └── SensorReadingResource.java         ← /api/v1/sensors/{id}/readings
    │   │
    │   ├── exception/
    │   │   ├── RoomNotEmptyException.java         ← Thrown on DELETE when sensors are linked
    │   │   ├── SensorUnavailableException.java    ← Thrown when sensor is in MAINTENANCE
    │   │   ├── LinkedResourceNotFoundException.java ← Thrown when roomId does not exist
    │   │   └── mapper/
    │   │       ├── RoomNotEmptyExceptionMapper.java           → HTTP 409
    │   │       ├── SensorUnavailableExceptionMapper.java      → HTTP 403
    │   │       ├── LinkedResourceNotFoundExceptionMapper.java → HTTP 422
    │   │       └── GlobalExceptionMapper.java                 → HTTP 500 (catch-all)
    │   │
    │   ├── filter/
    │   │   ├── LoggingFilter.java                 ← Request + response logging (@Provider)
    │   │   └── RootRedirectServlet.java           ← Redirects root context to API discovery
    │   │
    │   └── store/
    │       └── DataStore.java                     ← Static ConcurrentHashMap in-memory store
    │
    └── webapp/
        ├── index.html
        ├── META-INF/context.xml
        └── WEB-INF/web.xml
```

---

## 4. Architecture Overview

```
Client (curl / Postman / Browser)
        │
        │  HTTP Request
        ▼
┌──────────────────────────────────────────────────┐
│  Apache Tomcat 9  (Servlet Container)            │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │  Jersey 2.35  (JAX-RS Runtime)             │  │
│  │                                            │  │
│  │  LoggingFilter  ──────── every request     │  │
│  │       │                                    │  │
│  │  DiscoveryResource    GET /api/v1          │  │
│  │  RoomResource         /api/v1/rooms        │  │
│  │  SensorResource       /api/v1/sensors      │  │
│  │    └─ SensorReadingResource  /readings     │  │
│  │                                            │  │
│  │  ExceptionMappers ── domain → HTTP status  │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  DataStore (static ConcurrentHashMap)            │
│    rooms{} │ sensors{} │ readings{}              │
└──────────────────────────────────────────────────┘
```

**Thread safety:** `DataStore` uses `ConcurrentHashMap` for all three collections and `CopyOnWriteArrayList` for the per-room `sensorIds` list and per-sensor `readings` list. JAX-RS creates a new resource instance per request, so shared state **must not** live in resource fields — the static `DataStore` fields survive the entire application lifetime.

---

## 5. Build & Deployment

### Prerequisites

- Java 21+ JDK
- Apache Maven 3.8+
- Apache Tomcat 9.x

### Step 1 — Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/smart-campus-api.git
cd smart-campus-api
```

### Step 2 — Build the WAR

```bash
mvn clean package
```

This produces `target/smart-campus-api.war`.

### Step 3 — Deploy to Tomcat 9

```bash
# Copy the WAR into Tomcat's webapps directory
cp target/smart-campus-api.war /path/to/tomcat9/webapps/

# Start Tomcat
/path/to/tomcat9/bin/startup.sh      # Linux / macOS
/path/to/tomcat9/bin/startup.bat     # Windows
```

> **Tip:** PowerShell helper scripts for building and redeploying without Maven are available under `scripts/`.

### Step 4 — Verify the deployment

```bash
curl http://localhost:8080/smart-campus-api/api/v1
```

A successful response returns the discovery payload with `_links` to `/rooms` and `/sensors`.

---

## 6. API Reference

### Base URL

```
http://localhost:8080/smart-campus-api/api/v1
```

### Endpoints

| Method | Path | Description | Success | Error Codes |
|---|---|---|---|---|
| `GET` | `/` | Discovery — API metadata & HATEOAS links | `200` | — |
| `GET` | `/rooms` | List all rooms (full objects) | `200` | — |
| `POST` | `/rooms` | Create a new room | `201` | `400`, `409` |
| `GET` | `/rooms/{roomId}` | Get a single room | `200` | `404` |
| `DELETE` | `/rooms/{roomId}` | Delete a room (blocked if sensors exist) | `200` | `404`, `409` |
| `GET` | `/sensors` | List all sensors; supports `?type=` filter | `200` | — |
| `POST` | `/sensors` | Register a new sensor | `201` | `400`, `409`, `422` |
| `GET` | `/sensors/{sensorId}` | Get a single sensor | `200` | `404` |
| `GET` | `/sensors/{sensorId}/readings` | Fetch all readings for a sensor | `200` | `404` |
| `POST` | `/sensors/{sensorId}/readings` | Append a new reading | `201` | `400`, `403`, `404`, `503` |

### Query Parameters

| Endpoint | Parameter | Type | Description |
|---|---|---|---|
| `GET /sensors` | `type` | `string` | Case-insensitive filter — e.g. `?type=CO2` |

### Sensor Status Values

| Status | Accepts Readings | Notes |
|---|---|---|
| `ACTIVE` | ✅ Yes | Normal operating state |
| `MAINTENANCE` | ❌ No — `403` | Sensor under maintenance |
| `OFFLINE` | ❌ No — `503` | Sensor is offline |

---

## 7. Data Models

### Room

```json
{
  "id":        "ENG-201",
  "name":      "Engineering Lab B",
  "capacity":  40,
  "sensorIds": ["TEMP-001", "CO2-001"]
}
```

> `sensorIds` is managed server-side; any value sent on creation is discarded and replaced with an empty list.

### Sensor

```json
{
  "id":           "TEMP-002",
  "type":         "Temperature",
  "status":       "ACTIVE",
  "currentValue": 21.0,
  "roomId":       "ENG-201"
}
```

> `status` defaults to `ACTIVE` if omitted. Must be one of `ACTIVE`, `MAINTENANCE`, or `OFFLINE`.

### SensorReading

```json
{
  "id":        "a3f1c2d4-...",
  "timestamp": 1714300800000,
  "value":     23.7
}
```

> `id` (UUID) and `timestamp` (epoch ms) are server-generated if not provided by the client. Posting a new reading also updates `currentValue` on the parent sensor.

### Pre-loaded Seed Data

The `DataStore` static initialiser seeds the following on startup:

| Resource | ID | Details |
|---|---|---|
| Room | `LIB-301` | Library Quiet Study, capacity 50 |
| Room | `LAB-101` | Computer Lab A, capacity 30 |
| Sensor | `TEMP-001` | Temperature, ACTIVE, room `LIB-301` |
| Sensor | `CO2-001` | CO₂, ACTIVE, room `LAB-101` |
| Sensor | `OCC-001` | Occupancy, **MAINTENANCE**, room `LIB-301` |

---

## 8. Error Handling

All errors return a structured JSON body — never a raw stack trace.

```json
{
  "status":  409,
  "error":   "Room Not Empty",
  "message": "Room 'LIB-301' cannot be deleted. It still has 2 sensor(s) assigned: [OCC-001, TEMP-001]"
}
```

### HTTP Status Code Reference

| Code | Meaning | When It Occurs |
|---|---|---|
| `400` | Bad Request | Missing required field (`id`, `name`, `type`) or invalid value |
| `403` | Forbidden | Posting a reading to a `MAINTENANCE` sensor |
| `404` | Not Found | Room or sensor ID does not exist |
| `409` | Conflict | Duplicate ID on create, or deleting a room that still has sensors |
| `415` | Unsupported Media Type | Wrong `Content-Type` — JAX-RS enforces this automatically |
| `422` | Unprocessable Entity | Sensor created with a `roomId` that does not exist |
| `500` | Internal Server Error | Unhandled exception — logged server-side, generic message returned |
| `503` | Service Unavailable | Posting a reading to an `OFFLINE` sensor |

The `GlobalExceptionMapper` catches any uncaught `Throwable`, logs the full stack trace server-side only, and returns a safe `500` response to the client — preventing technology fingerprinting or internal architecture disclosure.

---

## 9. Sample curl Commands

### GET — Discovery endpoint

```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1 \
  -H "Accept: application/json"
```

### POST — Create a new room

```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-201","name":"Engineering Lab B","capacity":40}'
```

### GET — List sensors filtered by type

```bash
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2" \
  -H "Accept: application/json"
```

### POST — Register a new sensor

```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":21.0,"roomId":"ENG-201"}'
```

### POST — Add a reading to a sensor

```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-002/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.7}'
```

### DELETE — Attempt to delete a room with sensors (expect `409`)

```bash
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301 \
  -H "Accept: application/json"
```

### POST — Add reading to a `MAINTENANCE` sensor (expect `403`)

```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```

### POST — Create a sensor with an invalid `roomId` (expect `422`)

```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"GHOST-001","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"FAKE-999"}'
```

---

## 10. Coursework Report

---

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a **new instance of each resource class for every incoming HTTP request** (per-request scope). This means resource classes are not singletons — each request gets its own fresh object, and any instance variables declared inside a resource class are re-initialised on every request.

To preserve state across requests, shared data must live outside resource classes entirely. In this project, `DataStore` holds `static ConcurrentHashMap` fields that persist for the full lifetime of the application — independent of any resource instance lifecycle. Because multiple HTTP requests can arrive concurrently, those static collections must be thread-safe; `ConcurrentHashMap` is used instead of a plain `HashMap` to prevent race conditions such as two threads simultaneously inserting sensors and causing data corruption or lost updates.

---

### Part 1.2 — HATEOAS and Hypermedia in REST

HATEOAS (Hypermedia As The Engine Of Application State) means the API embeds navigational links inside its responses, so clients can discover available actions without consulting external documentation.

In this project, `GET /api/v1` returns a `_links` object containing URLs to `/api/v1/rooms` and `/api/v1/sensors`. A client can start at the root and traverse the entire API purely by following links in responses — analogous to how a browser user follows hyperlinks rather than typing URLs manually.

This benefits developers because paths do not need to be hard-coded. If a resource moves to a new path in a future version, the client receives the updated URL automatically via the link. It also makes the API self-documenting and more resilient to structural change.

---

### Part 2.1 — Returning Full Objects vs Just IDs

Returning **only IDs** on a list endpoint is bandwidth-efficient, but forces every client to make N additional requests to fetch full details for each item — the classic N+1 request problem. This increases latency, server load, and client-side complexity.

Returning **full objects** increases list response size but eliminates all follow-up requests. For most campus management scenarios — populating a room availability dashboard, for instance — clients need names and capacities immediately, making full objects the practical choice. For very large datasets, pagination or sparse fieldsets (`?fields=id,name`) can be introduced to balance the trade-off without sacrificing developer experience.

---

### Part 2.2 — Is DELETE Idempotent?

In this implementation, DELETE is **conditionally idempotent**. The first call to `DELETE /api/v1/rooms/{roomId}` on a valid, empty room deletes it and returns `200 OK`. A subsequent identical call returns `404 Not Found`.

REST defines idempotency in terms of **server state**, not response codes. Since the room is absent both after the first and second deletion, the server state is identical — satisfying the idempotency contract. The `404` on the second call is correct and expected behaviour for a resource that no longer exists; it is not a violation of the principle.

---

### Part 3.1 — `@Consumes(APPLICATION_JSON)` and Content-Type Mismatch

The `@Consumes(MediaType.APPLICATION_JSON)` annotation tells the JAX-RS runtime that a method only accepts requests with a `Content-Type: application/json` header.

If a client sends data as `text/plain` or `application/xml`, JAX-RS rejects the request before the method body is ever reached, returning `HTTP 415 Unsupported Media Type` automatically. No manual error handling is required for this case — content negotiation is enforced at the infrastructure level, which is one of the key advantages of declarative JAX-RS annotations over hand-written servlet code.

---

### Part 3.2 — `@QueryParam` vs Path Segment for Filtering

Using `@QueryParam` (e.g. `GET /api/v1/sensors?type=CO2`) is preferred for filtering because:

- **Semantics** — Query parameters express optional, variable constraints on a collection. A path segment such as `/sensors/type/CO2` implies that `type/CO2` is a distinct, addressable resource — which it is not.
- **Optionality** — `@QueryParam` is optional by nature; omitting it returns all sensors. A path-based approach would require a separate route for the unfiltered case.
- **Composability** — Multiple filters combine naturally (`?type=CO2&status=ACTIVE`), which becomes awkward with nested path segments.
- **REST conventions** — Collections are identified by their path (`/sensors`); filtering, sorting, and pagination are query concerns — exactly what query parameters are designed for.

---

### Part 4.1 — Sub-Resource Locator Pattern

The Sub-Resource Locator pattern allows a resource method to return an instance of another class that handles a sub-path, rather than concentrating all nested endpoints in one large class.

In this project, `SensorResource` delegates `/sensors/{sensorId}/readings` to `SensorReadingResource` via the locator method `getReadingsResource()`. The benefits are:

- **Separation of concerns** — `SensorResource` handles sensor CRUD; `SensorReadingResource` manages reading history. Each class has a single responsibility.
- **Maintainability** — In a large API, placing all nested paths in one class produces hundreds of methods. Sub-resources keep each class small and focused.
- **Testability** — Each sub-resource class can be unit-tested in isolation without bootstrapping the full resource tree.
- **Reusability** — A sub-resource class could theoretically be mounted under a different parent path without code duplication.

---

### Part 5.2 — Why `422` Is More Accurate Than `404` for a Bad `roomId` Reference

`404 Not Found` indicates that the **requested URL** does not exist. In this scenario, the request URL (`POST /api/v1/sensors`) is perfectly valid — the problem lies inside the JSON body.

`422 Unprocessable Entity` indicates that the server understood the request format and the URL is correct, but the **semantic content** of the payload is invalid. A `roomId` referencing a non-existent room is a business logic error in the payload — the JSON is well-formed, but logically inconsistent.

Returning `404` here would mislead clients into believing the sensor endpoint itself does not exist. `422` clearly communicates: *your request reached the right place, but the data inside it is invalid.*

---

### Part 5.4 — Cybersecurity Risks of Exposing Stack Traces

Returning raw Java stack traces in API responses is a serious security vulnerability for several reasons:

- **Technology fingerprinting** — Stack traces expose the exact framework, library versions, and class names in use (e.g. `org.glassfish.jersey`, `com.smartcampus`), enabling attackers to look up known CVEs for those specific versions.
- **Internal architecture disclosure** — Package names, class hierarchies, and method call chains reveal the internal structure of the application, making targeted attacks easier to craft.
- **File path leakage** — Stack traces often include absolute server paths (e.g. `/home/ubuntu/tomcat/webapps/...`), exposing directory structure that aids privilege escalation attempts.
- **Logic disclosure** — The sequence of method calls can reveal business logic, making it easier to construct inputs that trigger specific vulnerabilities.

`GlobalExceptionMapper` addresses all of these risks by logging the full stack trace server-side only, then returning a safe, generic message to the client — giving operators full observability without giving attackers any actionable information.

---

### Part 5.5 — Why Use Filters for Cross-Cutting Concerns

Inserting `Logger.info()` manually into every resource method violates the **DRY (Don't Repeat Yourself)** principle and introduces several practical problems:

- **Inconsistency** — A developer who forgets to add logging to a new endpoint creates invisible gaps in observability.
- **Maintenance burden** — If the log format changes, every single method must be updated individually.
- **Tight coupling** — Business logic becomes entangled with infrastructure concerns, making both harder to test and modify independently.

JAX-RS filters (implemented via `ContainerRequestFilter` and `ContainerResponseFilter`) are applied automatically to **every request and response** by the framework, with zero modification required in resource classes. Logging, authentication, CORS headers, and rate limiting all belong at the filter layer, not inside business logic. `LoggingFilter` in this project logs the HTTP method, full URI, and response status code for every interaction — producing clean, consistent, and reliable observability with no coupling to any resource class.

---

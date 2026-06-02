# InnerLink Backend

InnerLink Backend is a Vert.x-based Java service that powers the InnerLink application.
The Maven project lives in [innerlink-backend](innerlink-backend), while this root folder keeps the backend workspace organized.

## Overview

The backend provides:

- Authentication and session lookup endpoints.
- User profile read and update endpoints.
- Reflection/post creation and listing endpoints.
- Admin dashboard, member management, and emergency flag endpoints.
- Conversation and chat routing for volunteer, mood, and group matching flows.

## Tech Stack

- Java 17
- Vert.x 5.0.12
- Maven wrapper
- MySQL
- JDBC client for database access
- BCrypt for password hashing

## Project Structure

The main application code is under `innerlink-backend/src/main/java/com/innerlink/innerlink_backend`:

- `MainVerticle.java` starts the HTTP server on port `8888` and registers the routers.
- `config/` contains database bootstrap and schema setup.
- `controllers/`, `routes/`, and `services/` contain the main REST feature layers.
- `chat/` contains the chat router, controller, services, matching logic, and moderation helpers.

## Main Features

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/auth/me`
- `GET /api/users/:id`
- `PUT /api/users/:id`
- `GET /api/reflections`
- `POST /api/reflections`
- `GET /api/admin/dashboard/analytics`
- `GET /api/admin/community/members`
- `PATCH /api/admin/community/members/:userId/role`
- `DELETE /api/admin/community/members/:userId`
- `GET /api/conversations/user/:userId`
- `GET /api/conversations/:conversationId/messages`
- `POST /api/conversations/initiate`
- `POST /api/conversations/messages/offline-save`

## Requirements

- Java 17
- A running MySQL server on `localhost:3306`
- A database named `innerlink` will be created automatically if it does not exist

The current database configuration in `config/DatabaseConfig.java` uses the local MySQL `root` account and a hard-coded password.
If your environment differs, update that file before starting the app.

## Run

From the `innerlink-backend` folder:

```bash
./mvnw clean test
./mvnw clean package
./mvnw clean compile exec:java
```

On Windows, use:

```bash
.\mvnw.cmd clean test
.\mvnw.cmd clean package
.\mvnw.cmd clean compile exec:java
```

The server listens on `http://localhost:8888`.

## Notes

- CORS is enabled for the API routes.
- Chat endpoints are routed through Vert.x event bus handlers.
- The repository already includes a legacy AsciiDoc README in the Maven project folder.
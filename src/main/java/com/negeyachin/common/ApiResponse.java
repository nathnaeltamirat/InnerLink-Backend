package com.negeyachin.common;

import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;

/**
 * Builds the standard response envelope used by every endpoint:
 * {
 *   "success": true|false,
 *   "message": "...",
 *   "data":    { ... } | null,
 *   "timestamp": "..."
 * }
 */
public final class ApiResponse {

    private ApiResponse() {}

    public static JsonObject ok(String message, Object data) {
        JsonObject obj = new JsonObject()
            .put("success", true)
            .put("message", message)
            .put("timestamp", LocalDateTime.now().toString());
        if (data instanceof JsonObject jo) {
            obj.put("data", jo);
        } else if (data instanceof io.vertx.core.json.JsonArray ja) {
            obj.put("data", ja);
        } else if (data != null) {
            obj.put("data", JsonObject.mapFrom(data));
        }
        return obj;
    }

    public static JsonObject ok(Object data) {
        return ok("Success", data);
    }

    public static JsonObject error(int statusCode, String message) {
        return new JsonObject()
            .put("success", false)
            .put("statusCode", statusCode)
            .put("message", message)
            .put("timestamp", LocalDateTime.now().toString());
    }
}

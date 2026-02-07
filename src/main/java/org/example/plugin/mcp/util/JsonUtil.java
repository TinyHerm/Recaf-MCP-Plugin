package org.example.plugin.mcp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonUtil {

	private JsonUtil() {}

	public static String str(JsonElement el) {
		if (el == null || el.isJsonNull()) return null;
		if (el.isJsonPrimitive()) return el.getAsJsonPrimitive().getAsString();
		return null;
	}

	public static int integer(JsonElement el, int def) {
		if (el == null || el.isJsonNull()) return def;
		try { return el.getAsInt(); } catch (Exception e) { return def; }
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static JsonObject obj(JsonElement el) {
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	public static JsonArray arr(JsonElement el) {
		return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
	}

	public static JsonObject error(String message) {
		JsonObject o = new JsonObject();
		o.addProperty("error", message);
		return o;
	}

	public static JsonObject toolResult(JsonObject data, boolean isError) {
		JsonObject result = new JsonObject();
		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "text");
		text.addProperty("text", data.toString());
		content.add(text);
		result.add("content", content);
		if (!isError) result.add("structuredContent", data);
		result.addProperty("isError", isError);
		return result;
	}

	public static JsonObject rpcOk(JsonElement id, JsonObject result) {
		JsonObject o = new JsonObject();
		o.addProperty("jsonrpc", "2.0");
		o.add("id", id);
		o.add("result", result);
		return o;
	}

	public static JsonObject rpcError(JsonElement id, int code, String message, Object data) {
		JsonObject o = new JsonObject();
		o.addProperty("jsonrpc", "2.0");
		if (id != null) o.add("id", id);
		JsonObject err = new JsonObject();
		err.addProperty("code", code);
		err.addProperty("message", message);
		if (data != null) err.addProperty("data", String.valueOf(data));
		o.add("error", err);
		return o;
	}
}

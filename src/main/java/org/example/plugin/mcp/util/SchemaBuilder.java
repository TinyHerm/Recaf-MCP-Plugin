package org.example.plugin.mcp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class SchemaBuilder {

	private SchemaBuilder() {}

	public static JsonObject toolDef(String name, String title, String desc, JsonObject inputSchema) {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.addProperty("title", title);
		o.addProperty("description", desc);
		o.add("inputSchema", inputSchema);
		return o;
	}

	public static JsonObject object(JsonObject... props) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		JsonObject properties = new JsonObject();
		JsonArray required = new JsonArray();
		for (JsonObject p : props) {
			String name = p.remove("__name").getAsString();
			boolean req = p.remove("__required").getAsBoolean();
			properties.add(name, p);
			if (req) required.add(name);
		}
		schema.add("properties", properties);
		if (!required.isEmpty()) schema.add("required", required);
		return schema;
	}

	public static JsonObject prop(String name, JsonObject schema) {
		schema.addProperty("__name", name);
		schema.addProperty("__required", false);
		return schema;
	}

	public static JsonObject req(String name, JsonObject schema) {
		schema.addProperty("__name", name);
		schema.addProperty("__required", true);
		return schema;
	}

	public static JsonObject string(String desc) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "string");
		o.addProperty("description", desc);
		return o;
	}

	public static JsonObject integer(String desc) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "integer");
		o.addProperty("description", desc);
		return o;
	}

	public static JsonObject array(JsonObject items) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "array");
		o.add("items", items);
		return o;
	}
}

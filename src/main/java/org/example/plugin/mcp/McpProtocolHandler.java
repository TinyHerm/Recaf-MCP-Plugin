package org.example.plugin.mcp;

import com.google.gson.*;
import org.example.plugin.mcp.tool.McpTool;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.example.plugin.mcp.util.JsonUtil.*;

public final class McpProtocolHandler {

	private static final String PROTOCOL_VERSION = "2026-02-08";
	private static final Gson GSON = new Gson();
	private final Map<String, McpTool> tools = new LinkedHashMap<>();

	public void registerTool(McpTool tool) {
		tools.put(tool.name(), tool);
	}

	public String handle(String requestBody) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(requestBody);
		} catch (JsonSyntaxException e) {
			return GSON.toJson(rpcError(null, -32700, "Parse error", e.getMessage()));
		}

		if (parsed.isJsonArray()) {
			JsonArray batch = parsed.getAsJsonArray();
			JsonArray responses = new JsonArray();
			for (JsonElement el : batch) {
				if (!el.isJsonObject()) continue;
				JsonObject resp = dispatch(el.getAsJsonObject());
				if (resp != null) responses.add(resp);
			}
			return GSON.toJson(responses);
		}

		if (!parsed.isJsonObject()) {
			return GSON.toJson(rpcError(null, -32600, "Invalid Request", null));
		}

		JsonObject resp = dispatch(parsed.getAsJsonObject());
		return resp == null ? "" : GSON.toJson(resp);
	}

	private JsonObject dispatch(JsonObject req) {
		String method = str(req.get("method"));
		JsonElement id = req.get("id");
		boolean isNotification = (id == null || id.isJsonNull());

		if (method == null) {
			return isNotification ? null : rpcError(id, -32600, "Invalid Request", "Missing method");
		}

		return switch (method) {
			case "initialize" -> handleInitialize(id);
			case "notifications/initialized" -> null;
			case "ping" -> rpcOk(id, new JsonObject());
			case "tools/list" -> handleToolsList(id);
			case "tools/call" -> handleToolsCall(id, req);
			default -> isNotification ? null : rpcError(id, -32601, "Method not found", method);
		};
	}

	private JsonObject handleInitialize(JsonElement id) {
		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion", PROTOCOL_VERSION);

		JsonObject capabilities = new JsonObject();
		JsonObject toolsCap = new JsonObject();
		toolsCap.addProperty("listChanged", false);
		capabilities.add("tools", toolsCap);
		result.add("capabilities", capabilities);

		JsonObject serverInfo = new JsonObject();
		serverInfo.addProperty("name", "recaf-mcp");
		serverInfo.addProperty("version", "1.0.0");
		result.add("serverInfo", serverInfo);

		return rpcOk(id, result);
	}

	private JsonObject handleToolsList(JsonElement id) {
		JsonObject result = new JsonObject();
		JsonArray toolArray = new JsonArray();
		for (McpTool tool : tools.values()) {
			toolArray.add(tool.schema());
		}
		result.add("tools", toolArray);
		return rpcOk(id, result);
	}

	private JsonObject handleToolsCall(JsonElement id, JsonObject req) {
		JsonObject params = obj(req.get("params"));
		if (params == null) return rpcError(id, -32602, "Invalid params", null);

		String toolName = str(params.get("name"));
		if (toolName == null) return rpcError(id, -32602, "Missing tool name", null);

		McpTool tool = tools.get(toolName);
		if (tool == null) return rpcError(id, -32602, "Unknown tool: " + toolName, null);

		JsonObject arguments = obj(params.get("arguments"));
		if (arguments == null) arguments = new JsonObject();

		try {
			JsonObject data = tool.execute(arguments);
			boolean isError = data.has("error");
			return rpcOk(id, toolResult(data, isError));
		} catch (Exception e) {
			return rpcOk(id, toolResult(error("Tool threw: " + e.getMessage()), true));
		}
	}
}

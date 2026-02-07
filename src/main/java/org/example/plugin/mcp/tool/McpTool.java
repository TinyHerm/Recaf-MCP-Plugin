package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;

public interface McpTool {
	String name();
	JsonObject schema();
	JsonObject execute(JsonObject args);
}

package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.time.Instant;

import static org.example.plugin.mcp.util.JsonUtil.error;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class WorkspaceStatusTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public WorkspaceStatusTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_workspace_status"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Workspace status", "Get current workspace info", object());
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		JsonObject out = new JsonObject();
		out.addProperty("hasWorkspace", ws != null);
		out.addProperty("timestamp", Instant.now().toString());
		if (ws == null) return out;

		WorkspaceResource primary = ws.getPrimaryResource();
		out.addProperty("hasPrimaryResource", primary != null);
		if (primary != null) {
			JvmClassBundle jvm = primary.getJvmClassBundle();
			out.addProperty("jvmClassCount", jvm != null ? jvm.size() : 0);
		}
		return out;
	}
}

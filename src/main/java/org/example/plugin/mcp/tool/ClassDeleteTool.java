package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.builder.JvmClassInfoBuilder;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ClassDeleteTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public ClassDeleteTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_class_delete"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Delete class",
				"Remove a class from the workspace's primary resource",
				object(
						req("internalName", string("JVM internal class name to delete"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String name = str(args.get("internalName"));
		if (name == null || name.isBlank()) return error("Missing internalName");

		WorkspaceResource primary = ws.getPrimaryResource();
		if (primary == null) return error("No primary resource");
		JvmClassBundle bundle = primary.getJvmClassBundle();
		if (bundle == null) return error("No JVM class bundle");

		JvmClassInfo existing = bundle.get(name);
		if (existing == null) return error("Class not found: " + name);

		bundle.remove(name);

		JsonObject out = new JsonObject();
		out.addProperty("deleted", true);
		out.addProperty("className", name);
		return out;
	}
}

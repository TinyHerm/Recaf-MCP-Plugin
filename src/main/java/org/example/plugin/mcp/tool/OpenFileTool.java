package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import jakarta.enterprise.inject.Instance;
import software.coley.recaf.path.FilePathNode;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.model.Workspace;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class OpenFileTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final Instance<Actions> actions;

	public OpenFileTool(WorkspaceManager workspaceManager, Instance<Actions> actions) {
		this.workspaceManager = workspaceManager;
		this.actions = actions;
	}

	@Override
	public String name() {
		return "recaf_ui_open_file";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Open file in UI",
				"Open a non-class file (resource) in the Recaf UI.",
				object(
						req("fileName", string("File path within the workspace, e.g. META-INF/MANIFEST.MF"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();

		String fileName = str(args.get("fileName"));
		if (fileName == null || fileName.isBlank()) return error("Missing fileName");

		FilePathNode filePath = ws.findFile(fileName);
		if (filePath == null) return error("File not found: " + fileName);

		Actions actionsInstance;
		try {
			actionsInstance = actions.get();
		} catch (Exception e) {
			return error("UI not available: " + e.getMessage());
		}

		CompletableFuture<String> result = new CompletableFuture<>();
		FxThreadUtil.run(() -> {
			try {
				actionsInstance.gotoDeclaration(filePath);
				result.complete("Opened " + fileName);
			} catch (Exception e) {
				result.complete("Failed to open: " + e.getMessage());
			}
		});

		try {
			String message = result.get(5, TimeUnit.SECONDS);
			JsonObject out = new JsonObject();
			out.addProperty("opened", true);
			out.addProperty("fileName", fileName);
			out.addProperty("message", message);
			return out;
		} catch (Exception e) {
			return error("Timed out waiting for UI: " + e.getMessage());
		}
	}
}

package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import software.coley.recaf.info.FileInfo;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.FileBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.nio.charset.StandardCharsets;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ReadFileTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public ReadFileTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_file_read"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Read file",
				"Read a non-class file from the workspace (e.g. META-INF/MANIFEST.MF, config files, etc.)",
				object(
						req("path", string("File path within the workspace (e.g. META-INF/MANIFEST.MF)")),
						prop("maxChars", integer("Truncate text output to this many characters (default 60000)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String filePath = str(args.get("path"));
		if (filePath == null || filePath.isBlank()) return error("Missing path");

		int maxChars = clamp(integer(args.get("maxChars"), 60000), 1000, 500000);

		WorkspaceResource primary = ws.getPrimaryResource();
		if (primary == null) return error("No primary resource");
		FileBundle fileBundle = primary.getFileBundle();
		if (fileBundle == null) return error("No file bundle");

		FileInfo fileInfo = fileBundle.get(filePath);
		if (fileInfo == null) return error("File not found: " + filePath);

		byte[] rawContent = fileInfo.getRawContent();
		int size = rawContent != null ? rawContent.length : 0;

		JsonObject out = new JsonObject();
		out.addProperty("path", filePath);
		out.addProperty("size", size);

		if (rawContent != null) {
			String text = new String(rawContent, StandardCharsets.UTF_8);
			boolean truncated = text.length() > maxChars;
			if (truncated) text = text.substring(0, maxChars);
			out.addProperty("content", text);
			out.addProperty("truncated", truncated);
		}

		return out;
	}
}

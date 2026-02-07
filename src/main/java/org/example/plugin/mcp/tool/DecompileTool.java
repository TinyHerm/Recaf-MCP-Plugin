package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.workspace.WorkspaceManager;

import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.decompile.DecompileResult;
import software.coley.recaf.workspace.model.Workspace;

import java.util.concurrent.TimeUnit;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class DecompileTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final DecompilerManager decompilerManager;

	public DecompileTool(WorkspaceManager workspaceManager, DecompilerManager decompilerManager) {
		this.workspaceManager = workspaceManager;
		this.decompilerManager = decompilerManager;
	}

	@Override
	public String name() { return "recaf_class_decompile"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Decompile class", "Decompile a JVM class to Java source", object(
				req("internalName", string("JVM internal class name, like com/example/Foo")),
				prop("timeout", integer("Timeout in ms (default 5000)")),
				prop("maxChars", integer("Truncate output to this many characters (default 60000)"))
		));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String name = str(args.get("internalName"));
		if (name == null || name.isBlank()) return error("Missing internalName");

		ClassPathNode path = ws.findJvmClass(name);
		if (path == null) return error("Class not found: " + name);

		JvmClassInfo info = path.getValue().asJvmClass();
		int timeout = clamp(integer(args.get("timeout"), 5000), 500, 30000);
		int maxChars = clamp(integer(args.get("maxChars"), 60000), 1000, 500000);

		try {
			DecompileResult result = decompilerManager.decompile(ws, info)
					.get(timeout, TimeUnit.MILLISECONDS);
			String text = result.getText();
			if (text == null) return error("Decompilation returned null");

			boolean truncated = text.length() > maxChars;
			if (truncated)
				text = text.substring(0, maxChars) + "\n/* truncated */\n";

			JsonObject out = new JsonObject();
			out.addProperty("className", name);
			out.addProperty("source", text);
			out.addProperty("truncated", truncated);
			return out;
		} catch (Exception e) {
			return error("Decompilation failed: " + e.getMessage());
		}
	}
}

package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.darknet.assembler.error.Error;
import me.darknet.assembler.error.Result;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.assembler.AssemblerPipelineManager;
import software.coley.recaf.services.assembler.JvmAssemblerPipeline;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class DisassembleTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final AssemblerPipelineManager assemblerPipelineManager;

	public DisassembleTool(WorkspaceManager workspaceManager, AssemblerPipelineManager assemblerPipelineManager) {
		this.workspaceManager = workspaceManager;
		this.assemblerPipelineManager = assemblerPipelineManager;
	}

	@Override
	public String name() { return "recaf_class_disassemble"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Disassemble class", "Disassemble a JVM class to assembler text", object(
				req("internalName", string("JVM internal class name, like com/example/Foo")),
				prop("maxChars", integer("Truncate output to this many characters (default 120000)"))
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

		int maxChars = clamp(integer(args.get("maxChars"), 120000), 2000, 1000000);

		JvmAssemblerPipeline pipeline = assemblerPipelineManager.newJvmAssemblerPipeline(ws);
		try {
			Result<String> result = pipeline.disassemble(path);
			if (!result.isOk()) return assemblerErrors("Disassemble failed", result);

			String text = result.get();
			if (text == null) return error("Disassembly returned null");

			boolean truncated = text.length() > maxChars;
			if (truncated)
				text = text.substring(0, maxChars) + "\n// truncated\n";

			JsonObject out = new JsonObject();
			out.addProperty("className", name);
			out.addProperty("disassembly", text);
			out.addProperty("truncated", truncated);
			return out;
		} catch (Exception e) {
			return error("Disassembly failed: " + e.getMessage());
		} finally {
			try { pipeline.close(); } catch (Exception ignored) {}
		}
	}

	private static JsonObject assemblerErrors(String message, Result<?> result) {
		JsonObject out = new JsonObject();
		out.addProperty("error", message);
		JsonArray errors = new JsonArray();
		try {
			for (Error e : result.errors()) {
				JsonObject eo = new JsonObject();
				eo.addProperty("message", e.getMessage());
				var loc = e.getLocation();
				if (loc != null) {
					eo.addProperty("line", loc.line());
					eo.addProperty("column", loc.column());
				}
				errors.add(eo);
				if (errors.size() >= 50) break;
			}
		} catch (Exception ignored) {}
		out.add("errors", errors);
		return out;
	}
}

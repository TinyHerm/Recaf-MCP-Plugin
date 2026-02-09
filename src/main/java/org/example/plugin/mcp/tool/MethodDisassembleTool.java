package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.darknet.assembler.error.Error;
import me.darknet.assembler.error.Result;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.path.ClassMemberPathNode;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.assembler.AssemblerPipelineManager;
import software.coley.recaf.services.assembler.JvmAssemblerPipeline;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class MethodDisassembleTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final AssemblerPipelineManager assemblerPipelineManager;

	public MethodDisassembleTool(WorkspaceManager workspaceManager, AssemblerPipelineManager assemblerPipelineManager) {
		this.workspaceManager = workspaceManager;
		this.assemblerPipelineManager = assemblerPipelineManager;
	}

	@Override
	public String name() { return "recaf_method_disassemble"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Disassemble method",
				"Disassemble a single method to assembler text",
				object(
						req("internalName", string("JVM internal class name")),
						req("methodName", string("Method name")),
						req("methodDesc", string("Method descriptor (e.g. (I)V)")),
						prop("maxChars", integer("Truncate output (default 60000)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String className = str(args.get("internalName"));
		if (className == null || className.isBlank()) return error("Missing internalName");
		String methodName = str(args.get("methodName"));
		if (methodName == null || methodName.isBlank()) return error("Missing methodName");
		String methodDesc = str(args.get("methodDesc"));
		if (methodDesc == null || methodDesc.isBlank()) return error("Missing methodDesc");

		int maxChars = clamp(integer(args.get("maxChars"), 60000), 1000, 500000);

		ClassPathNode classPath = ws.findJvmClass(className);
		if (classPath == null) return error("Class not found: " + className);

		var classInfo = classPath.getValue().asJvmClass();
		MethodMember targetMethod = null;
		for (var m : classInfo.getMethods()) {
			if (m.getName().equals(methodName) && m.getDescriptor().equals(methodDesc)) {
				targetMethod = m;
				break;
			}
		}
		if (targetMethod == null) return error("Method not found: " + methodName + methodDesc);

		ClassMemberPathNode memberPath = classPath.child(targetMethod);
		if (memberPath == null) return error("Could not create member path");

		JvmAssemblerPipeline pipeline = assemblerPipelineManager.newJvmAssemblerPipeline(ws);
		try {
			Result<String> result = pipeline.disassemble(memberPath);
			if (!result.isOk()) {
				JsonObject out = new JsonObject();
				out.addProperty("error", "Disassemble failed");
				JsonArray errors = new JsonArray();
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
				out.add("errors", errors);
				return out;
			}

			String text = result.get();
			if (text == null) return error("Disassembly returned null");

			boolean truncated = text.length() > maxChars;
			if (truncated) text = text.substring(0, maxChars);

			JsonObject out = new JsonObject();
			out.addProperty("className", className);
			out.addProperty("methodName", methodName);
			out.addProperty("methodDesc", methodDesc);
			out.addProperty("disassembly", text);
			out.addProperty("truncated", truncated);
			return out;
		} catch (Exception e) {
			return error("Disassembly failed: " + e.getMessage());
		} finally {
			try { pipeline.close(); } catch (Exception ignored) {}
		}
	}
}

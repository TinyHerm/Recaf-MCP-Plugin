package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.darknet.assembler.ast.ASTElement;
import me.darknet.assembler.error.Error;
import me.darknet.assembler.error.Result;
import me.darknet.assembler.parser.Token;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.assembler.AssemblerPipelineManager;
import software.coley.recaf.services.assembler.JvmAssemblerPipeline;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class AssembleTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final AssemblerPipelineManager assemblerPipelineManager;

	public AssembleTool(WorkspaceManager workspaceManager, AssemblerPipelineManager assemblerPipelineManager) {
		this.workspaceManager = workspaceManager;
		this.assemblerPipelineManager = assemblerPipelineManager;
	}

	@Override
	public String name() { return "recaf_class_assemble_apply"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Assemble & apply", "Assemble source and apply the resulting class to the workspace", object(
				req("internalName", string("Target class internal name, like com/example/Foo")),
				req("source", string("Full assembler source text"))
		));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String name = str(args.get("internalName"));
		if (name == null || name.isBlank()) return error("Missing internalName");
		String source = str(args.get("source"));
		if (source == null || source.isBlank()) return error("Missing source");

		ClassPathNode path = ws.findJvmClass(name);
		if (path == null) return error("Class not found: " + name);

		JvmAssemblerPipeline pipeline = assemblerPipelineManager.newJvmAssemblerPipeline(ws);
		try {
			Result<List<Token>> tokens = pipeline.tokenize(source, "mcp-input");
			if (!tokens.isOk()) return assemblerErrors("Tokenize failed", tokens);

			Result<List<ASTElement>> ast = pipeline.fullParse(tokens.get());
			if (!ast.isOk()) return assemblerErrors("Parse failed", ast);

			Result<JvmClassInfo> assembled = pipeline.assembleAndWrap(ast.get(), path);
			if (!assembled.isOk()) return assemblerErrors("Assembly failed", assembled);

			JvmClassInfo newInfo = assembled.get();
			if (newInfo == null) return error("Assembler yielded no class");

			if (!name.equals(newInfo.getName()))
				return error("Refusing to apply: assembled class name differs (" + newInfo.getName() + ")");

			WorkspaceResource primary = ws.getPrimaryResource();
			if (primary == null) return error("No primary resource");
			JvmClassBundle bundle = primary.getJvmClassBundle();
			if (bundle == null) return error("No JVM class bundle in primary resource");
			if (bundle.get(name) == null) return error("Class is not in the primary resource: " + name);
			bundle.put(newInfo);

			int bytecodeSize = newInfo.getBytecode() != null ? newInfo.getBytecode().length : 0;

			JsonObject out = new JsonObject();
			out.addProperty("internalName", name);
			out.addProperty("compiled", true);
			out.addProperty("applied", true);
			out.addProperty("bytecodeSize", bytecodeSize);
			return out;
		} catch (Exception e) {
			return error("Assembly failed: " + e.getMessage());
		} finally {
			try { pipeline.close(); } catch (Exception ignored) {}
		}
	}

	private static JsonObject assemblerErrors(String phase, Result<?> result) {
		JsonObject out = new JsonObject();
		out.addProperty("error", phase);
		JsonArray arr = new JsonArray();
		try {
			for (Error err : result.errors()) {
				JsonObject eo = new JsonObject();
				eo.addProperty("message", err.getMessage());
				var loc = err.getLocation();
				if (loc != null) {
					eo.addProperty("line", loc.line());
					eo.addProperty("column", loc.column());
				}
				arr.add(eo);
				if (arr.size() >= 50) break;
			}
		} catch (Exception ignored) {}
		out.add("errors", arr);
		return out;
	}
}

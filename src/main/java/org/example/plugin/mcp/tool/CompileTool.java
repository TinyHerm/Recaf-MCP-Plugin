package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.compile.CompilerDiagnostic;
import software.coley.recaf.services.compile.CompilerResult;
import software.coley.recaf.services.compile.JavacArguments;
import software.coley.recaf.services.compile.JavacArgumentsBuilder;
import software.coley.recaf.services.compile.JavacCompiler;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class CompileTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final JavacCompiler javacCompiler;

	public CompileTool(WorkspaceManager workspaceManager, JavacCompiler javacCompiler) {
		this.workspaceManager = workspaceManager;
		this.javacCompiler = javacCompiler;
	}

	@Override
	public String name() { return "recaf_compile_java"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Compile Java",
				"Compile Java source code and apply the resulting class to the workspace. "
						+ "The class name must match an existing class in the workspace.",
				object(
						req("className", string("Fully qualified class name using dots (e.g. com.example.Foo)")),
						req("source", string("Full Java source code")),
						prop("targetVersion", integer("Java target version (default: current JVM version)")),
						prop("debug", bool("Include debug information (default true)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		if (!JavacCompiler.isAvailable()) return error("Java compiler is not available");

		String className = str(args.get("className"));
		if (className == null || className.isBlank()) return error("Missing className");
		String source = str(args.get("source"));
		if (source == null || source.isBlank()) return error("Missing source");

		int targetVersion = integer(args.get("targetVersion"), -1);
		boolean debug = bool(args.get("debug"), true);

		String internalName = className.replace('.', '/');

		try {
			JavacArgumentsBuilder builder = new JavacArgumentsBuilder()
					.withClassName(className)
					.withClassSource(source)
					.withDebugVariables(debug)
					.withDebugLineNumbers(debug)
					.withDebugSourceName(debug);

			if (targetVersion > 0) {
				builder.withVersionTarget(targetVersion);
			}

			JavacArguments javacArgs = builder.build();
			CompilerResult result = javacCompiler.compile(javacArgs, ws, null);

			if (!result.wasSuccess()) {
				JsonObject out = new JsonObject();
				out.addProperty("compiled", false);

				JsonArray diagnostics = new JsonArray();
				if (result.getDiagnostics() != null) {
					for (CompilerDiagnostic d : result.getDiagnostics()) {
						JsonObject diag = new JsonObject();
						diag.addProperty("line", d.line());
						diag.addProperty("column", d.column());
						diag.addProperty("message", d.message());
						diag.addProperty("level", d.level().name());
						diagnostics.add(diag);
					}
				}
				out.add("diagnostics", diagnostics);
				if (result.getException() != null) {
					out.addProperty("exception", result.getException().getMessage());
				}
				return out;
			}

			byte[] bytecode = result.getCompilations().get(className);
			if (bytecode == null) return error("Compilation produced no output for: " + className);

			WorkspaceResource primary = ws.getPrimaryResource();
			if (primary == null) return error("No primary resource");
			JvmClassBundle bundle = primary.getJvmClassBundle();
			if (bundle == null) return error("No JVM class bundle");

			var existing = bundle.get(internalName);
			if (existing == null) return error("Class not in workspace: " + internalName);

			var builder2 = existing.toJvmClassBuilder();
			builder2.adaptFrom(new org.objectweb.asm.ClassReader(bytecode));
			bundle.put(builder2.build());

			JsonObject out = new JsonObject();
			out.addProperty("compiled", true);
			out.addProperty("applied", true);
			out.addProperty("className", className);
			out.addProperty("bytecodeSize", bytecode.length);
			return out;
		} catch (Exception e) {
			return error("Compilation failed: " + e.getMessage());
		}
	}
}

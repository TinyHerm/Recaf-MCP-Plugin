package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.decompile.DecompileResult;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.decompile.JvmDecompiler;
import software.coley.recaf.services.decompile.filter.JvmBytecodeFilter;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.util.visitors.MemberFilteringVisitor;
import software.coley.recaf.workspace.model.Workspace;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
	public String name() {
		return "recaf_class_decompile";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Decompile class", "Decompile a JVM class to Java source", object(
				req("internalName", string("JVM internal class name, like com/example/Foo")),
				prop("decompiler", string("Decompiler to use: CFR, Vineflower, Procyon, Fallback. "
						+ "If omitted, the currently configured decompiler is used.")),
				prop("methodName", string("Isolate decompilation to this single method (requires methodDesc)")),
				prop("methodDesc", string("Method descriptor for method isolation (e.g. (I)V)")),
				prop("timeout", integer("Timeout in ms (default 5000)")),
				prop("maxChars", integer("Truncate output to this many characters (default 60000)"))
		));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();

		String name = str(args.get("internalName"));
		if (name == null || name.isBlank()) return error("Missing internalName");

		ClassPathNode path = ws.findJvmClass(name);
		if (path == null) return error("Class not found: " + name);

		JvmClassInfo info = path.getValue().asJvmClass();
		int timeout = clamp(integer(args.get("timeout"), 5000), 500, 30000);
		int maxChars = clamp(integer(args.get("maxChars"), 60000), 1000, 500000);

		String decompilerName = str(args.get("decompiler"));
		JvmDecompiler decompiler;
		if (decompilerName != null && !decompilerName.isBlank()) {
			decompiler = decompilerManager.getJvmDecompiler(decompilerName);
			if (decompiler == null) {
				String available = decompilerManager.getJvmDecompilers().stream()
						.map(JvmDecompiler::getName)
						.collect(Collectors.joining(", "));
				return error("Unknown decompiler: " + decompilerName + ". Available: " + available);
			}
		} else {
			decompiler = decompilerManager.getTargetJvmDecompiler();
		}

		String methodName = str(args.get("methodName"));
		String methodDesc = str(args.get("methodDesc"));
		JvmBytecodeFilter memberFilter = null;
		if (methodName != null && !methodName.isBlank()
				&& methodDesc != null && !methodDesc.isBlank()) {
			MethodMember target = null;
			for (var m : info.getMethods()) {
				if (m.getName().equals(methodName) && m.getDescriptor().equals(methodDesc)) {
					target = m;
					break;
				}
			}
			if (target == null)
				return error("Method not found: " + methodName + methodDesc + " in " + name);

			final MethodMember t = target;
			memberFilter = (workspace, classInfo, bytecode) -> {
				ClassReader reader = new ClassReader(bytecode);
				ClassWriter writer = new ClassWriter(0);
				reader.accept(new MemberFilteringVisitor(writer, t), 0);
				return writer.toByteArray();
			};
		}

		if (memberFilter != null)
			decompilerManager.addJvmBytecodeFilter(memberFilter);
		try {
			DecompileResult result = decompilerManager.decompile(decompiler, ws, info)
					.get(timeout, TimeUnit.MILLISECONDS);
			String text = result.getText();
			if (text == null) return error("Decompilation returned null");

			boolean truncated = text.length() > maxChars;
			if (truncated) text = text.substring(0, maxChars) + "\n/* truncated */\n";

			JsonObject out = new JsonObject();
			out.addProperty("className", name);
			out.addProperty("decompiler", decompiler.getName());
			if (methodName != null && methodDesc != null) {
				out.addProperty("methodName", methodName);
				out.addProperty("methodDesc", methodDesc);
			}
			out.addProperty("source", text);
			out.addProperty("truncated", truncated);

			JsonArray available = new JsonArray();
			for (JvmDecompiler d : decompilerManager.getJvmDecompilers())
				available.add(d.getName());
			out.add("availableDecompilers", available);

			return out;
		} catch (Exception e) {
			return error("Decompilation failed: " + e.getMessage());
		} finally {
			if (memberFilter != null)
				decompilerManager.removeJvmBytecodeFilter(memberFilter);
		}
	}
}

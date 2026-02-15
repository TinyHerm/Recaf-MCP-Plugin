package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.util.BlwUtil;
import software.coley.recaf.workspace.model.Workspace;

import java.util.ArrayList;
import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class MethodBytecodeTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public MethodBytecodeTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() {
		return "recaf_method_bytecode";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Method bytecode (JASM)",
				"Get method bytecode instructions in JASM assembler format. "
						+ "Output can be used as a basis for modifications via the assembler tool.",
				object(
						req("internalName", string("JVM internal class name, like com/example/Foo")),
						req("methodName", string("Method name (e.g. main, <init>)")),
						req("methodDesc", string("Method descriptor (e.g. ([Ljava/lang/String;)V)")),
						prop("maxChars", integer("Truncate output to this many characters (default 60000)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();

		String className = str(args.get("internalName"));
		if (className == null || className.isBlank()) return error("Missing internalName");
		String methodName = str(args.get("methodName"));
		if (methodName == null || methodName.isBlank()) return error("Missing methodName");
		String methodDesc = str(args.get("methodDesc"));
		if (methodDesc == null || methodDesc.isBlank()) return error("Missing methodDesc");

		int maxChars = clamp(integer(args.get("maxChars"), 60000), 1000, 500000);

		ClassPathNode path = ws.findJvmClass(className);
		if (path == null) return error("Class not found: " + className);

		JvmClassInfo info = path.getValue().asJvmClass();
		ClassNode classNode = new ClassNode();
		info.getClassReader().accept(classNode, 0);

		MethodNode target = null;
		if (classNode.methods != null)
			for (MethodNode mn : classNode.methods)
				if (mn.name.equals(methodName) && mn.desc.equals(methodDesc)) {
					target = mn;
					break;
				}
		if (target == null)
			return error("Method not found: " + methodName + methodDesc + " in " + className);

		String jasm;
		try {
			List<AbstractInsnNode> insns = new ArrayList<>();
			if (target.instructions != null)
				for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext())
					insns.add(insn);
			jasm = BlwUtil.toString(insns);
		} catch (Exception e) {
			return error("Failed to convert bytecode to JASM: " + e.getMessage());
		}

		boolean truncated = jasm.length() > maxChars;
		if (truncated) jasm = jasm.substring(0, maxChars) + "\n// truncated\n";

		JsonObject out = new JsonObject();
		out.addProperty("className", className);
		out.addProperty("methodName", methodName);
		out.addProperty("methodDesc", methodDesc);
		out.addProperty("access", target.access);
		out.addProperty("maxStack", target.maxStack);
		out.addProperty("maxLocals", target.maxLocals);
		out.addProperty("jasm", jasm);
		out.addProperty("truncated", truncated);
		return out;
	}
}

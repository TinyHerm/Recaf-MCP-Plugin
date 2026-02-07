package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ClassOutlineTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public ClassOutlineTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_class_get_outline"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Class outline", "Get fields/methods signatures (no code)", object(
				req("internalName", string("JVM internal class name, like com/example/Foo"))
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
		ClassNode node = new ClassNode();
		info.getClassReader().accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		JsonObject out = new JsonObject();
		out.addProperty("name", node.name);
		out.addProperty("superName", node.superName);
		out.addProperty("access", node.access);

		JsonArray ifaces = new JsonArray();
		if (node.interfaces != null) node.interfaces.forEach(i -> ifaces.add(String.valueOf(i)));
		out.add("interfaces", ifaces);

		JsonArray fields = new JsonArray();
		if (node.fields != null) {
			for (FieldNode fn : node.fields) {
				JsonObject fo = new JsonObject();
				fo.addProperty("name", fn.name);
				fo.addProperty("desc", fn.desc);
				fo.addProperty("access", fn.access);
				fields.add(fo);
			}
		}
		out.add("fields", fields);

		JsonArray methods = new JsonArray();
		if (node.methods != null) {
			for (MethodNode mn : node.methods) {
				JsonObject mo = new JsonObject();
				mo.addProperty("name", mn.name);
				mo.addProperty("desc", mn.desc);
				mo.addProperty("access", mn.access);
				methods.add(mo);
			}
		}
		out.add("methods", methods);
		return out;
	}
}

package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ClassInfoTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public ClassInfoTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_class_info"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Class info",
				"Get detailed class information including access flags, version, annotations, "
						+ "source file, signature, inner classes, and more",
				object(
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
		info.getClassReader().accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

		JsonObject out = new JsonObject();
		out.addProperty("name", node.name);
		out.addProperty("superName", node.superName);
		out.addProperty("access", node.access);
		out.addProperty("accessReadable", decodeAccess(node.access));
		out.addProperty("version", node.version);
		out.addProperty("javaVersion", node.version - 44);

		if (node.signature != null) out.addProperty("signature", node.signature);
		if (node.sourceFile != null) out.addProperty("sourceFile", node.sourceFile);
		if (node.sourceDebug != null) out.addProperty("sourceDebug", node.sourceDebug);
		if (node.outerClass != null) out.addProperty("outerClass", node.outerClass);
		if (node.outerMethod != null) out.addProperty("outerMethod", node.outerMethod);
		if (node.outerMethodDesc != null) out.addProperty("outerMethodDesc", node.outerMethodDesc);
		if (node.module != null) out.addProperty("moduleName", node.module.name);

		JsonArray ifaces = new JsonArray();
		if (node.interfaces != null) node.interfaces.forEach(i -> ifaces.add(String.valueOf(i)));
		out.add("interfaces", ifaces);

		if (node.visibleAnnotations != null && !node.visibleAnnotations.isEmpty()) {
			out.add("annotations", buildAnnotations(node.visibleAnnotations));
		}
		if (node.invisibleAnnotations != null && !node.invisibleAnnotations.isEmpty()) {
			out.add("invisibleAnnotations", buildAnnotations(node.invisibleAnnotations));
		}

		if (node.innerClasses != null && !node.innerClasses.isEmpty()) {
			JsonArray inners = new JsonArray();
			for (InnerClassNode ic : node.innerClasses) {
				JsonObject ico = new JsonObject();
				ico.addProperty("name", ic.name);
				if (ic.outerName != null) ico.addProperty("outerName", ic.outerName);
				if (ic.innerName != null) ico.addProperty("innerName", ic.innerName);
				ico.addProperty("access", ic.access);
				inners.add(ico);
			}
			out.add("innerClasses", inners);
		}

		JsonArray fields = new JsonArray();
		if (node.fields != null) {
			for (FieldNode fn : node.fields) {
				JsonObject fo = new JsonObject();
				fo.addProperty("name", fn.name);
				fo.addProperty("desc", fn.desc);
				fo.addProperty("access", fn.access);
				fo.addProperty("accessReadable", decodeFieldAccess(fn.access));
				if (fn.signature != null) fo.addProperty("signature", fn.signature);
				if (fn.value != null) fo.addProperty("value", String.valueOf(fn.value));
				if (fn.visibleAnnotations != null && !fn.visibleAnnotations.isEmpty())
					fo.add("annotations", buildAnnotations(fn.visibleAnnotations));
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
				mo.addProperty("accessReadable", decodeMethodAccess(mn.access));
				if (mn.signature != null) mo.addProperty("signature", mn.signature);
				if (mn.exceptions != null && !mn.exceptions.isEmpty()) {
					JsonArray exceptions = new JsonArray();
					mn.exceptions.forEach(exceptions::add);
					mo.add("exceptions", exceptions);
				}
				if (mn.visibleAnnotations != null && !mn.visibleAnnotations.isEmpty())
					mo.add("annotations", buildAnnotations(mn.visibleAnnotations));
				methods.add(mo);
			}
		}
		out.add("methods", methods);

		int bytecodeSize = info.getBytecode() != null ? info.getBytecode().length : 0;
		out.addProperty("bytecodeSize", bytecodeSize);

		return out;
	}

	private static JsonArray buildAnnotations(java.util.List<AnnotationNode> annotations) {
		JsonArray arr = new JsonArray();
		for (AnnotationNode an : annotations) {
			JsonObject ao = new JsonObject();
			ao.addProperty("descriptor", an.desc);
			if (an.values != null && !an.values.isEmpty()) {
				JsonObject values = new JsonObject();
				for (int i = 0; i < an.values.size(); i += 2) {
					String key = (String) an.values.get(i);
					Object val = an.values.get(i + 1);
					values.addProperty(key, String.valueOf(val));
				}
				ao.add("values", values);
			}
			arr.add(ao);
		}
		return arr;
	}

	private static String decodeAccess(int access) {
		StringBuilder sb = new StringBuilder();
		if ((access & 0x0001) != 0) sb.append("public ");
		if ((access & 0x0010) != 0) sb.append("final ");
		if ((access & 0x0020) != 0) sb.append("super ");
		if ((access & 0x0200) != 0) sb.append("interface ");
		if ((access & 0x0400) != 0) sb.append("abstract ");
		if ((access & 0x1000) != 0) sb.append("synthetic ");
		if ((access & 0x2000) != 0) sb.append("annotation ");
		if ((access & 0x4000) != 0) sb.append("enum ");
		if ((access & 0x8000) != 0) sb.append("module ");
		return sb.toString().trim();
	}

	private static String decodeFieldAccess(int access) {
		StringBuilder sb = new StringBuilder();
		if ((access & 0x0001) != 0) sb.append("public ");
		if ((access & 0x0002) != 0) sb.append("private ");
		if ((access & 0x0004) != 0) sb.append("protected ");
		if ((access & 0x0008) != 0) sb.append("static ");
		if ((access & 0x0010) != 0) sb.append("final ");
		if ((access & 0x0040) != 0) sb.append("volatile ");
		if ((access & 0x0080) != 0) sb.append("transient ");
		if ((access & 0x1000) != 0) sb.append("synthetic ");
		if ((access & 0x4000) != 0) sb.append("enum ");
		return sb.toString().trim();
	}

	private static String decodeMethodAccess(int access) {
		StringBuilder sb = new StringBuilder();
		if ((access & 0x0001) != 0) sb.append("public ");
		if ((access & 0x0002) != 0) sb.append("private ");
		if ((access & 0x0004) != 0) sb.append("protected ");
		if ((access & 0x0008) != 0) sb.append("static ");
		if ((access & 0x0010) != 0) sb.append("final ");
		if ((access & 0x0020) != 0) sb.append("synchronized ");
		if ((access & 0x0040) != 0) sb.append("bridge ");
		if ((access & 0x0080) != 0) sb.append("varargs ");
		if ((access & 0x0100) != 0) sb.append("native ");
		if ((access & 0x0400) != 0) sb.append("abstract ");
		if ((access & 0x0800) != 0) sb.append("strict ");
		if ((access & 0x1000) != 0) sb.append("synthetic ");
		return sb.toString().trim();
	}
}

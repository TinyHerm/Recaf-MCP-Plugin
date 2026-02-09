package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class MethodBytecodeTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public MethodBytecodeTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_method_bytecode"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Method bytecode",
				"Get the bytecode instructions of a specific method. "
						+ "Returns opcode-level instruction listing, try-catch blocks, max stack/locals.",
				object(
						req("internalName", string("JVM internal class name, like com/example/Foo")),
						req("methodName", string("Method name (e.g. main, <init>)")),
						req("methodDesc", string("Method descriptor (e.g. ([Ljava/lang/String;)V)"))
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

		ClassPathNode path = ws.findJvmClass(className);
		if (path == null) return error("Class not found: " + className);

		JvmClassInfo info = path.getValue().asJvmClass();
		ClassNode classNode = new ClassNode();
		info.getClassReader().accept(classNode, 0);

		MethodNode target = null;
		if (classNode.methods != null) {
			for (MethodNode mn : classNode.methods) {
				if (mn.name.equals(methodName) && mn.desc.equals(methodDesc)) {
					target = mn;
					break;
				}
			}
		}
		if (target == null)
			return error("Method not found: " + methodName + methodDesc + " in " + className);

		JsonObject out = new JsonObject();
		out.addProperty("className", className);
		out.addProperty("methodName", methodName);
		out.addProperty("methodDesc", methodDesc);
		out.addProperty("access", target.access);
		out.addProperty("maxStack", target.maxStack);
		out.addProperty("maxLocals", target.maxLocals);

		// Instructions
		JsonArray instructions = new JsonArray();
		if (target.instructions != null) {
			int index = 0;
			for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				JsonObject insnObj = formatInstruction(insn, index);
				if (insnObj != null) {
					instructions.add(insnObj);
				}
				index++;
			}
		}
		out.add("instructions", instructions);

		// Try-catch blocks
		JsonArray tryCatch = new JsonArray();
		if (target.tryCatchBlocks != null) {
			for (TryCatchBlockNode tcb : target.tryCatchBlocks) {
				JsonObject tc = new JsonObject();
				tc.addProperty("start", target.instructions.indexOf(tcb.start));
				tc.addProperty("end", target.instructions.indexOf(tcb.end));
				tc.addProperty("handler", target.instructions.indexOf(tcb.handler));
				tc.addProperty("type", tcb.type != null ? tcb.type : "any");
				tryCatch.add(tc);
			}
		}
		out.add("tryCatchBlocks", tryCatch);

		// Local variables
		JsonArray locals = new JsonArray();
		if (target.localVariables != null) {
			for (LocalVariableNode lv : target.localVariables) {
				JsonObject lo = new JsonObject();
				lo.addProperty("name", lv.name);
				lo.addProperty("desc", lv.desc);
				lo.addProperty("index", lv.index);
				locals.add(lo);
			}
		}
		out.add("localVariables", locals);

		return out;
	}

	private static JsonObject formatInstruction(AbstractInsnNode insn, int index) {
		int opcode = insn.getOpcode();
		String opcodeName = (opcode >= 0 && opcode < Printer.OPCODES.length)
				? Printer.OPCODES[opcode]
				: null;

		JsonObject obj = new JsonObject();
		obj.addProperty("index", index);

		switch (insn) {
			case LabelNode ignored -> {
				return null; // skip labels, they clutter the output
			}
			case LineNumberNode ln -> {
				obj.addProperty("type", "LINE");
				obj.addProperty("line", ln.line);
				return obj;
			}
			case FrameNode ignored -> {
				return null; // skip frames
			}
			case InsnNode ignored -> {
				obj.addProperty("opcode", opcodeName);
			}
			case IntInsnNode ii -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("operand", ii.operand);
			}
			case VarInsnNode vi -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("var", vi.var);
			}
			case TypeInsnNode ti -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("desc", ti.desc);
			}
			case FieldInsnNode fi -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("owner", fi.owner);
				obj.addProperty("name", fi.name);
				obj.addProperty("desc", fi.desc);
			}
			case MethodInsnNode mi -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("owner", mi.owner);
				obj.addProperty("name", mi.name);
				obj.addProperty("desc", mi.desc);
			}
			case InvokeDynamicInsnNode id -> {
				obj.addProperty("opcode", "INVOKEDYNAMIC");
				obj.addProperty("name", id.name);
				obj.addProperty("desc", id.desc);
				obj.addProperty("bsm", id.bsm.toString());
			}
			case JumpInsnNode ji -> {
				obj.addProperty("opcode", opcodeName);
				obj.addProperty("target", insn.getNext() != null ? "label" : "?");
			}
			case LdcInsnNode ldc -> {
				obj.addProperty("opcode", "LDC");
				obj.addProperty("value", String.valueOf(ldc.cst));
				obj.addProperty("constType", ldc.cst.getClass().getSimpleName());
			}
			case IincInsnNode iinc -> {
				obj.addProperty("opcode", "IINC");
				obj.addProperty("var", iinc.var);
				obj.addProperty("incr", iinc.incr);
			}
			case TableSwitchInsnNode ts -> {
				obj.addProperty("opcode", "TABLESWITCH");
				obj.addProperty("min", ts.min);
				obj.addProperty("max", ts.max);
			}
			case LookupSwitchInsnNode ls -> {
				obj.addProperty("opcode", "LOOKUPSWITCH");
				JsonArray keys = new JsonArray();
				if (ls.keys != null) ls.keys.forEach(keys::add);
				obj.add("keys", keys);
			}
			case MultiANewArrayInsnNode ma -> {
				obj.addProperty("opcode", "MULTIANEWARRAY");
				obj.addProperty("desc", ma.desc);
				obj.addProperty("dims", ma.dims);
			}
			default -> {
				obj.addProperty("opcode", opcodeName != null ? opcodeName : "UNKNOWN");
			}
		}

		return obj;
	}
}

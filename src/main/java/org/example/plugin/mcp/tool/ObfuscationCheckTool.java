package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;

import java.util.*;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ObfuscationCheckTool implements McpTool {

	private static final String ALL_CHECKS = "strings,invokedynamic,math,flow,names";

	private final WorkspaceManager workspaceManager;

	public ObfuscationCheckTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() {
		return "recaf_check_obfuscation";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Check obfuscation",
				"Analyze the workspace for common obfuscation indicators: "
						+ "string encryption, reference encryption (invokedynamic), "
						+ "constant encryption (math ops), flow obfuscation, name obfuscation.",
				object(
						prop("maxClasses", integer("Max classes to analyze (default 200, max 2000)")),
						prop("checks", string("Comma-separated checks: " + ALL_CHECKS + ". Default: all"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();
		JvmClassBundle bundle = ws.getPrimaryResource().getJvmClassBundle();
		if (bundle == null) return error("No JVM classes in workspace");

		int maxClasses = clamp(integer(args.get("maxClasses"), 200), 10, 2000);
		Set<String> checks = parseSet(str(args.get("checks")));

		List<JvmClassInfo> classes = new ArrayList<>();
		for (JvmClassInfo ci : bundle) {
			classes.add(ci);
			if (classes.size() >= maxClasses) break;
		}

		List<ParsedClass> parsed = new ArrayList<>(classes.size());
		for (JvmClassInfo ci : classes) {
			try {
				ClassNode node = new ClassNode();
				ci.getClassReader().accept(node, ClassReader.SKIP_DEBUG);
				parsed.add(new ParsedClass(ci, node));
			} catch (Exception ignored) {
			}
		}

		JsonObject out = new JsonObject();
		out.addProperty("totalClasses", bundle.size());
		out.addProperty("analyzedClasses", classes.size());

		if (checks.contains("strings")) out.add("stringEncryption", checkStrings(parsed));
		if (checks.contains("invokedynamic")) out.add("referenceEncryption", checkInvokeDynamic(parsed));
		if (checks.contains("math")) out.add("constantEncryption", checkMath(parsed));
		if (checks.contains("flow")) out.add("flowObfuscation", checkFlow(parsed));
		if (checks.contains("names")) out.add("nameObfuscation", checkNames(classes));

		return out;
	}

	private Set<String> parseSet(String str) {
		if (str == null || str.isBlank())
			return Set.of("strings", "invokedynamic", "math", "flow", "names");
		Set<String> result = new HashSet<>();
		for (String part : str.split(","))
			result.add(part.trim().toLowerCase());
		return result;
	}

	private JsonObject checkStrings(List<ParsedClass> entries) {
		int total = 0, suspect = 0;
		JsonArray examples = new JsonArray();

		for (ParsedClass entry : entries) {
			if (entry.node.methods == null) continue;
			for (MethodNode mn : entry.node.methods) {
				if (mn.instructions == null) continue;
				for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						total++;
						if (isHighEntropy(s)) {
							suspect++;
							if (examples.size() < 10) {
								JsonObject ex = new JsonObject();
								ex.addProperty("value", s.length() > 80 ? s.substring(0, 80) + "..." : s);
								ex.addProperty("entropy", String.format("%.2f", entropy(s)));
								ex.addProperty("className", entry.info.getName());
								ex.addProperty("method", mn.name + mn.desc);
								examples.add(ex);
							}
						}
					}
				}
			}
		}

		JsonObject r = new JsonObject();
		r.addProperty("totalStrings", total);
		r.addProperty("suspectHighEntropyStrings", suspect);
		r.addProperty("confidence", total == 0 ? "none"
				: suspect > total * 0.3 ? "high"
				: suspect > total * 0.1 ? "medium" : "low");
		r.add("examples", examples);
		return r;
	}

	private JsonObject checkInvokeDynamic(List<ParsedClass> entries) {
		int total = 0, nonStandard = 0;
		JsonArray suspects = new JsonArray();

		for (ParsedClass entry : entries) {
			if (entry.node.methods == null) continue;
			int clsNs = 0, clsTotal = 0;
			for (MethodNode mn : entry.node.methods) {
				if (mn.instructions == null) continue;
				for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof InvokeDynamicInsnNode indy) {
						clsTotal++;
						total++;
						String bsm = indy.bsm.getOwner();
						if (!bsm.equals("java/lang/invoke/LambdaMetafactory")
								&& !bsm.equals("java/lang/invoke/StringConcatFactory")) {
							clsNs++;
							nonStandard++;
						}
					}
				}
			}
			if (clsNs > 0 && suspects.size() < 20) {
				JsonObject sc = new JsonObject();
				sc.addProperty("className", entry.info.getName());
				sc.addProperty("nonStandardIndy", clsNs);
				sc.addProperty("totalIndy", clsTotal);
				suspects.add(sc);
			}
		}

		JsonObject r = new JsonObject();
		r.addProperty("totalInvokeDynamic", total);
		r.addProperty("nonStandardInvokeDynamic", nonStandard);
		r.addProperty("confidence", nonStandard > 10 ? "high" : nonStandard > 0 ? "medium" : "none");
		r.add("suspectClasses", suspects);
		return r;
	}

	private JsonObject checkMath(List<ParsedClass> entries) {
		int totalInsns = 0, mathInsns = 0;
		JsonArray suspects = new JsonArray();

		for (ParsedClass entry : entries) {
			if (entry.node.methods == null) continue;
			int clsMath = 0, clsTotal = 0;
			for (MethodNode mn : entry.node.methods) {
				if (mn.instructions == null) continue;
				for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					int op = insn.getOpcode();
					if (op < 0) continue;
					clsTotal++;
					totalInsns++;
					if (isMathOp(op)) { clsMath++; mathInsns++; }
				}
			}
			if (clsTotal > 20 && clsMath > clsTotal * 0.25 && suspects.size() < 20) {
				JsonObject sc = new JsonObject();
				sc.addProperty("className", entry.info.getName());
				sc.addProperty("mathOps", clsMath);
				sc.addProperty("totalOps", clsTotal);
				sc.addProperty("ratio", String.format("%.1f%%", clsMath * 100.0 / clsTotal));
				suspects.add(sc);
			}
		}

		double ratio = totalInsns > 0 ? mathInsns * 100.0 / totalInsns : 0;
		JsonObject r = new JsonObject();
		r.addProperty("totalInstructions", totalInsns);
		r.addProperty("mathInstructions", mathInsns);
		r.addProperty("overallRatio", String.format("%.1f%%", ratio));
		r.addProperty("confidence", ratio > 15 ? "high" : ratio > 8 ? "medium" : "low");
		r.add("suspectClasses", suspects);
		return r;
	}

	private JsonObject checkFlow(List<ParsedClass> entries) {
		int totalInsns = 0, flowInsns = 0;
		JsonArray suspects = new JsonArray();

		for (ParsedClass entry : entries) {
			if (entry.node.methods == null) continue;
			int clsFlow = 0, clsTotal = 0;
			for (MethodNode mn : entry.node.methods) {
				if (mn.instructions == null) continue;
				for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					int op = insn.getOpcode();
					if (op < 0) continue;
					clsTotal++;
					totalInsns++;
					if (isFlowOp(op) || insn instanceof TableSwitchInsnNode
							|| insn instanceof LookupSwitchInsnNode) {
						clsFlow++;
						flowInsns++;
					}
				}
			}
			if (clsTotal > 20 && clsFlow > clsTotal * 0.20 && suspects.size() < 20) {
				JsonObject sc = new JsonObject();
				sc.addProperty("className", entry.info.getName());
				sc.addProperty("flowOps", clsFlow);
				sc.addProperty("totalOps", clsTotal);
				sc.addProperty("ratio", String.format("%.1f%%", clsFlow * 100.0 / clsTotal));
				suspects.add(sc);
			}
		}

		double ratio = totalInsns > 0 ? flowInsns * 100.0 / totalInsns : 0;
		JsonObject r = new JsonObject();
		r.addProperty("totalInstructions", totalInsns);
		r.addProperty("flowInstructions", flowInsns);
		r.addProperty("overallRatio", String.format("%.1f%%", ratio));
		r.addProperty("confidence", ratio > 15 ? "high" : ratio > 8 ? "medium" : "low");
		r.add("suspectClasses", suspects);
		return r;
	}

	private JsonObject checkNames(List<JvmClassInfo> classes) {
		int shortNames = 0, nonAscii = 0, total = 0;

		for (JvmClassInfo ci : classes) {
			String simple = ci.getName();
			int slash = simple.lastIndexOf('/');
			if (slash >= 0) simple = simple.substring(slash + 1);
			total++;
			if (simple.length() <= 2) shortNames++;
			if (!isAsciiIdent(simple)) nonAscii++;

			for (var f : ci.getFields()) {
				total++;
				if (f.getName().length() <= 2) shortNames++;
				if (!isAsciiIdent(f.getName())) nonAscii++;
			}
			for (var m : ci.getMethods()) {
				if (m.getName().startsWith("<")) continue;
				total++;
				if (m.getName().length() <= 2) shortNames++;
				if (!isAsciiIdent(m.getName())) nonAscii++;
			}
		}

		double shortRatio = total > 0 ? shortNames * 100.0 / total : 0;
		double nonAsciiRatio = total > 0 ? nonAscii * 100.0 / total : 0;

		JsonObject r = new JsonObject();
		r.addProperty("totalNames", total);
		r.addProperty("shortNames", shortNames);
		r.addProperty("nonAsciiNames", nonAscii);
		r.addProperty("shortNameRatio", String.format("%.1f%%", shortRatio));
		r.addProperty("nonAsciiRatio", String.format("%.1f%%", nonAsciiRatio));
		r.addProperty("confidence",
				shortRatio > 40 || nonAsciiRatio > 5 ? "high"
						: shortRatio > 15 || nonAsciiRatio > 1 ? "medium" : "low");
		return r;
	}

	private record ParsedClass(JvmClassInfo info, ClassNode node) {}

	private static boolean isHighEntropy(String s) {
		if (s.length() < 6) return false;
		double e = entropy(s);
		if (e > 4.5) return true;
		int unusual = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x20 || c > 0x7E) unusual++;
		}
		return unusual > s.length() * 0.3 && e > 3.5;
	}

	private static double entropy(String s) {
		if (s == null || s.isEmpty()) return 0;
		int[] freq = new int[256];
		for (int i = 0; i < s.length(); i++)
			freq[s.charAt(i) & 0xFF]++;
		double e = 0, len = s.length();
		for (int f : freq) {
			if (f == 0) continue;
			double p = f / len;
			e -= p * (Math.log(p) / Math.log(2));
		}
		return e;
	}

	private static boolean isMathOp(int op) {
		return (op >= Opcodes.IADD && op <= Opcodes.LXOR);
	}

	private static boolean isFlowOp(int op) {
		return (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE)
				|| op == Opcodes.GOTO || op == Opcodes.JSR
				|| op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
	}

	private static boolean isAsciiIdent(String name) {
		if (name == null || name.isEmpty()) return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9') || c == '_' || c == '$'))
				return false;
		}
		return true;
	}
}

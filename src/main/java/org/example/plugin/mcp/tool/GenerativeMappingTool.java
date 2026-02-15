package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.member.FieldMember;
import software.coley.recaf.info.member.LocalVariable;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.services.inheritance.InheritanceGraph;
import software.coley.recaf.services.inheritance.InheritanceGraphService;
import software.coley.recaf.services.mapping.IntermediateMappings;
import software.coley.recaf.services.mapping.MappingApplierService;
import software.coley.recaf.services.mapping.MappingResults;
import software.coley.recaf.services.mapping.Mappings;
import software.coley.recaf.services.mapping.gen.MappingGenerator;
import software.coley.recaf.services.mapping.gen.filter.NameGeneratorFilter;
import software.coley.recaf.services.mapping.gen.naming.AlphabetNameGenerator;
import software.coley.recaf.services.mapping.gen.naming.DeconflictingNameGenerator;
import software.coley.recaf.services.mapping.gen.naming.IncrementingNameGenerator;
import software.coley.recaf.services.mapping.gen.naming.NameGenerator;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class GenerativeMappingTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final MappingGenerator mappingGenerator;
	private final MappingApplierService mappingApplierService;
	private final InheritanceGraphService inheritanceGraphService;

	public GenerativeMappingTool(WorkspaceManager workspaceManager,
	                             MappingGenerator mappingGenerator,
	                             MappingApplierService mappingApplierService,
	                             InheritanceGraphService inheritanceGraphService) {
		this.workspaceManager = workspaceManager;
		this.mappingGenerator = mappingGenerator;
		this.mappingApplierService = mappingApplierService;
		this.inheritanceGraphService = inheritanceGraphService;
	}

	@Override
	public String name() {
		return "recaf_mapping_generate";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Generate mappings",
				"Generate and apply mappings to rename obfuscated names in the workspace. "
						+ "Targets only likely obfuscated/random names and keeps readable names unchanged.",
				object(
						prop("style", enumString(
								"Naming style: 'incremental' (Class1, field1, method1) "
										+ "or 'alphabetic' (pseudo-random alphabetic). Default: incremental",
								"incremental", "alphabetic")),
						prop("apply", bool("Apply the generated mappings immediately (default: true)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();
		WorkspaceResource primary = ws.getPrimaryResource();

		String style = str(args.get("style"));
		if (style == null || style.isBlank()) style = "incremental";
		boolean apply = bool(args.get("apply"), true);

		NameGenerator generator = switch (style) {
			case "alphabetic" -> new AlphabetNameGenerator("abcdefghijklmnopqrstuvwxyz", 6);
			default -> new IncrementingNameGenerator();
		};
		if (generator instanceof DeconflictingNameGenerator dcg)
			dcg.setWorkspace(ws);

		InheritanceGraph graph = inheritanceGraphService.getCurrentWorkspaceInheritanceGraph();
		if (graph == null)
			graph = inheritanceGraphService.newInheritanceGraph(ws);

		try {
			Mappings mappings = mappingGenerator.generate(ws, primary, graph, generator, new ObfuscatedNameFilter());
			IntermediateMappings intermediate = mappings.exportIntermediate();

			int classCount = intermediate.getClasses().size();
			int fieldCount = intermediate.getFields().values().stream().mapToInt(List::size).sum();
			int methodCount = intermediate.getMethods().values().stream().mapToInt(List::size).sum();
			int totalCount = classCount + fieldCount + methodCount;

			JsonObject out = new JsonObject();
			out.addProperty("style", style);
			out.addProperty("classesRenamed", classCount);
			out.addProperty("fieldsRenamed", fieldCount);
			out.addProperty("methodsRenamed", methodCount);
			out.addProperty("renameCount", totalCount);
			out.add("classChanges", toClassChanges(intermediate, 120));
			out.add("fieldChanges", toFieldChanges(intermediate, 120));
			out.add("methodChanges", toMethodChanges(intermediate, 120));

			if (totalCount == 0) {
				out.addProperty("applied", false);
				out.addProperty("message", "No likely obfuscated names found");
				return out;
			}

			if (apply) {
				MappingResults results = mappingApplierService.inWorkspace(ws)
						.applyToPrimaryResource(intermediate);
				results.apply();
				out.addProperty("applied", true);
			} else {
				out.addProperty("applied", false);
			}

			return out;
		} catch (Exception e) {
			return error("Mapping generation failed: " + e.getMessage());
		}
	}

	private static JsonArray toClassChanges(IntermediateMappings mappings, int limit) {
		JsonArray arr = new JsonArray();
		int count = 0;
		for (var mapping : mappings.getClasses().values()) {
			if (count++ >= limit) break;
			JsonObject change = new JsonObject();
			change.addProperty("from", mapping.getOldName());
			change.addProperty("to", mapping.getNewName());
			arr.add(change);
		}
		return arr;
	}

	private static JsonArray toFieldChanges(IntermediateMappings mappings, int limit) {
		JsonArray arr = new JsonArray();
		int count = 0;
		for (var list : mappings.getFields().values()) {
			for (var mapping : list) {
				if (count++ >= limit) return arr;
				JsonObject change = new JsonObject();
				change.addProperty("owner", mapping.getOwnerName());
				change.addProperty("desc", mapping.getDesc());
				change.addProperty("from", mapping.getOldName());
				change.addProperty("to", mapping.getNewName());
				arr.add(change);
			}
		}
		return arr;
	}

	private static JsonArray toMethodChanges(IntermediateMappings mappings, int limit) {
		JsonArray arr = new JsonArray();
		int count = 0;
		for (var list : mappings.getMethods().values()) {
			for (var mapping : list) {
				if (count++ >= limit) return arr;
				JsonObject change = new JsonObject();
				change.addProperty("owner", mapping.getOwnerName());
				change.addProperty("desc", mapping.getDesc());
				change.addProperty("from", mapping.getOldName());
				change.addProperty("to", mapping.getNewName());
				arr.add(change);
			}
		}
		return arr;
	}

	private static final class ObfuscatedNameFilter extends NameGeneratorFilter {
		private ObfuscatedNameFilter() {
			super(null, false);
		}

		@Override
		public boolean shouldMapClass(ClassInfo info) {
			String className = simpleClassName(info.getName());
			if ("module-info".equals(className) || "package-info".equals(className)) return false;
			return isLikelyObfuscated(className);
		}

		@Override
		public boolean shouldMapField(ClassInfo owner, FieldMember field) {
			return isLikelyObfuscated(field.getName());
		}

		@Override
		public boolean shouldMapMethod(ClassInfo owner, MethodMember method) {
			String name = method.getName();
			if (name.startsWith("<")) return false;
			return isLikelyObfuscated(name);
		}

		@Override
		public boolean shouldMapLocalVariable(ClassInfo owner, MethodMember declaringMethod, LocalVariable variable) {
			return isLikelyObfuscated(variable.getName());
		}

		private static String simpleClassName(String internalName) {
			int slash = internalName.lastIndexOf('/');
			String base = slash >= 0 ? internalName.substring(slash + 1) : internalName;
			int dollar = base.lastIndexOf('$');
			if (dollar >= 0 && dollar + 1 < base.length())
				return base.substring(dollar + 1);
			return base;
		}

		private static boolean isLikelyObfuscated(String name) {
			if (name == null || name.isBlank()) return false;
			int len = name.length();
			if (len == 1) return true;
			if (len == 2) {
				char a = name.charAt(0), b = name.charAt(1);
				if (a == b) return true;
				if (isAmbiguousChar(a) && isAmbiguousChar(b)) return true;
			}
			if (!isAsciiIdentifier(name)) return true;
			if (isAmbiguousName(name)) return true;
			if (isDigitHeavy(name)) return true;
			if (isConsonantOnlyLong(name)) return true;
			return false;
		}

		private static boolean isAsciiIdentifier(String name) {
			if (name.isEmpty()) return false;
			char c0 = name.charAt(0);
			if (!((c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z') || c0 == '_' || c0 == '$'))
				return false;
			for (int i = 1; i < name.length(); i++) {
				char c = name.charAt(i);
				if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
						|| (c >= '0' && c <= '9') || c == '_' || c == '$'))
					return false;
			}
			return true;
		}

		private static boolean isAmbiguousName(String name) {
			if (name.length() < 4) return false;
			int ambiguous = 0;
			for (int i = 0; i < name.length(); i++)
				if (isAmbiguousChar(name.charAt(i))) ambiguous++;
			return ambiguous * 100 / name.length() >= 75;
		}

		private static boolean isAmbiguousChar(char c) {
			return c == 'I' || c == 'l' || c == '1' || c == 'O' || c == '0' || c == 'o';
		}

		private static boolean isDigitHeavy(String name) {
			if (name.length() < 6) return false;
			int digits = 0;
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if (c >= '0' && c <= '9') digits++;
			}
			return digits * 100 / name.length() >= 50;
		}

		private static boolean isConsonantOnlyLong(String name) {
			if (name.length() < 10) return false;
			int letters = 0;
			int vowels = 0;
			for (int i = 0; i < name.length(); i++) {
				char c = Character.toLowerCase(name.charAt(i));
				if (c >= 'a' && c <= 'z') {
					letters++;
					if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u')
						vowels++;
				}
			}
			return letters >= 10 && vowels == 0;
		}
	}
}

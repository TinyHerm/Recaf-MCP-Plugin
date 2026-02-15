package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.mapping.MappingApplierService;
import software.coley.recaf.services.mapping.MappingResults;
import software.coley.recaf.services.mapping.IntermediateMappings;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class MappingTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final MappingApplierService mappingApplierService;

	public MappingTool(WorkspaceManager workspaceManager, MappingApplierService mappingApplierService) {
		this.workspaceManager = workspaceManager;
		this.mappingApplierService = mappingApplierService;
	}

	@Override
	public String name() { return "recaf_mapping_apply"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Apply mappings", "Rename classes, fields, and methods", object(
				prop("classRenames", array(object(
						req("from", string("Original internal class name")),
						req("to", string("New internal class name"))
				))),
				prop("fieldRenames", array(object(
						req("owner", string("Owning class internal name")),
						req("desc", string("Field descriptor")),
						req("from", string("Original field name")),
						req("to", string("New field name"))
				))),
				prop("methodRenames", array(object(
						req("owner", string("Owning class internal name")),
						req("desc", string("Method descriptor")),
						req("from", string("Original method name")),
						req("to", string("New method name"))
				)))
		));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();

		IntermediateMappings mappings = new IntermediateMappings();
		int classCount = 0;
		int fieldCount = 0;
		int methodCount = 0;
		JsonArray classChanges = new JsonArray();
		JsonArray fieldChanges = new JsonArray();
		JsonArray methodChanges = new JsonArray();

		JsonArray classRenames = arr(args.get("classRenames"));
		if (classRenames != null) {
			for (var el : classRenames) {
				JsonObject cr = obj(el);
				if (cr == null) continue;
				String from = str(cr.get("from"));
				String to = str(cr.get("to"));
				if (from != null && to != null) {
					mappings.addClass(from, to);
					classCount++;
					JsonObject change = new JsonObject();
					change.addProperty("from", from);
					change.addProperty("to", to);
					classChanges.add(change);
				}
			}
		}

		JsonArray fieldRenames = arr(args.get("fieldRenames"));
		if (fieldRenames != null) {
			for (var el : fieldRenames) {
				JsonObject fr = obj(el);
				if (fr == null) continue;
				String owner = str(fr.get("owner"));
				String desc = str(fr.get("desc"));
				String from = str(fr.get("from"));
				String to = str(fr.get("to"));
				if (owner != null && desc != null && from != null && to != null) {
					mappings.addField(owner, desc, from, to);
					fieldCount++;
					JsonObject change = new JsonObject();
					change.addProperty("owner", owner);
					change.addProperty("desc", desc);
					change.addProperty("from", from);
					change.addProperty("to", to);
					fieldChanges.add(change);
				}
			}
		}

		JsonArray methodRenames = arr(args.get("methodRenames"));
		if (methodRenames != null) {
			for (var el : methodRenames) {
				JsonObject mr = obj(el);
				if (mr == null) continue;
				String owner = str(mr.get("owner"));
				String desc = str(mr.get("desc"));
				String from = str(mr.get("from"));
				String to = str(mr.get("to"));
				if (owner != null && desc != null && from != null && to != null) {
					mappings.addMethod(owner, desc, from, to);
					methodCount++;
					JsonObject change = new JsonObject();
					change.addProperty("owner", owner);
					change.addProperty("desc", desc);
					change.addProperty("from", from);
					change.addProperty("to", to);
					methodChanges.add(change);
				}
			}
		}

		int count = classCount + fieldCount + methodCount;
		if (count == 0) return error("No valid renames provided");

		try {
			MappingResults results = mappingApplierService.inWorkspace(ws)
					.applyToPrimaryResource(mappings);
			results.apply();

			JsonObject out = new JsonObject();
			out.addProperty("applied", true);
			out.addProperty("classesRenamed", classCount);
			out.addProperty("fieldsRenamed", fieldCount);
			out.addProperty("methodsRenamed", methodCount);
			out.addProperty("renameCount", count);
			out.add("classChanges", classChanges);
			out.add("fieldChanges", fieldChanges);
			out.add("methodChanges", methodChanges);
			return out;
		} catch (Exception e) {
			return error("Mapping failed: " + e.getMessage());
		}
	}
}

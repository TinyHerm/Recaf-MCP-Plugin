package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.services.workspace.patch.PatchApplier;
import software.coley.recaf.services.workspace.patch.PatchFeedback;
import software.coley.recaf.services.workspace.patch.PatchProvider;
import software.coley.recaf.services.workspace.patch.model.WorkspacePatch;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class PatchTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final PatchProvider patchProvider;
	private final PatchApplier patchApplier;

	public PatchTool(WorkspaceManager workspaceManager, PatchProvider patchProvider, PatchApplier patchApplier) {
		this.workspaceManager = workspaceManager;
		this.patchProvider = patchProvider;
		this.patchApplier = patchApplier;
	}

	@Override
	public String name() { return "recaf_patch"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Patch operations",
				"Create a patch from current workspace changes, serialize it, or apply a patch from JSON. "
						+ "Use action=create to generate, action=apply to apply.",
				object(
						req("action", enumString("Patch action", "create", "apply")),
						prop("patchJson", string("Patch JSON string (required for apply action)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String action = str(args.get("action"));
		if (action == null) return error("Missing action");

		return switch (action) {
			case "create" -> createPatch(ws);
			case "apply" -> applyPatch(ws, args);
			default -> error("Invalid action: " + action);
		};
	}

	private JsonObject createPatch(Workspace ws) {
		try {
			WorkspacePatch patch = patchProvider.createPatch(ws);
			String json = patchProvider.serializePatch(patch);

			JsonObject out = new JsonObject();
			out.addProperty("created", true);
			out.addProperty("assemblerPatches", patch.jvmAssemblerPatches().size());
			out.addProperty("textFilePatches", patch.textFilePatches().size());
			out.addProperty("removals", patch.removals().size());
			out.addProperty("patchJson", json);
			return out;
		} catch (Exception e) {
			return error("Patch creation failed: " + e.getMessage());
		}
	}

	private JsonObject applyPatch(Workspace ws, JsonObject args) {
		String patchJson = str(args.get("patchJson"));
		if (patchJson == null || patchJson.isBlank()) return error("Missing patchJson");

		try {
			WorkspacePatch patch = patchProvider.deserializePatch(ws, patchJson);

			JsonArray errors = new JsonArray();
			PatchFeedback feedback = new PatchFeedback() {
				@Override
				public void onAssemblerErrorsObserved(java.util.List<me.darknet.assembler.error.Error> errs) {
					for (var e : errs) {
						JsonObject eo = new JsonObject();
						eo.addProperty("type", "assembler");
						eo.addProperty("message", e.getMessage());
						errors.add(eo);
					}
				}
			};

			boolean success = patchApplier.apply(patch, feedback);

			JsonObject out = new JsonObject();
			out.addProperty("applied", success);
			if (!errors.isEmpty()) out.add("errors", errors);
			return out;
		} catch (Exception e) {
			return error("Patch apply failed: " + e.getMessage());
		}
	}
}

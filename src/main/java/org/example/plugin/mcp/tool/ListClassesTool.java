package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class ListClassesTool implements McpTool {

	private final WorkspaceManager workspaceManager;

	public ListClassesTool(WorkspaceManager workspaceManager) {
		this.workspaceManager = workspaceManager;
	}

	@Override
	public String name() { return "recaf_workspace_list_classes"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "List classes", "List JVM classes from the primary resource", object(
				prop("prefix", string("Only include classes starting with this internal-name prefix")),
				prop("cursor", string("Pagination cursor returned by the previous call")),
				prop("limit", integer("Max items to return (default 200, max 1000)"))
		));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		JvmClassBundle jvm = ws.getPrimaryResource().getJvmClassBundle();
		if (jvm == null) return error("No JVM class bundle");

		String prefix = str(args.get("prefix"));
		int limit = clamp(integer(args.get("limit"), 200), 1, 1000);
		int offset = Math.max(0, integer(args.get("cursor"), 0));

		List<String> names = new ArrayList<>(jvm.size());
		for (JvmClassInfo info : jvm) {
			String n = info.getName();
			if (prefix == null || n.startsWith(prefix)) names.add(n);
		}
		names.sort(Comparator.naturalOrder());

		int end = Math.min(names.size(), offset + limit);
		List<String> slice = offset >= names.size() ? List.of() : names.subList(offset, end);
		String nextCursor = end < names.size() ? String.valueOf(end) : null;

		JsonObject out = new JsonObject();
		out.addProperty("count", slice.size());
		out.addProperty("totalMatched", names.size());
		if (nextCursor != null) out.addProperty("nextCursor", nextCursor);
		JsonArray arr = new JsonArray();
		for (String n : slice) arr.add(new JsonPrimitive(n));
		out.add("classes", arr);
		return out;
	}
}

package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.search.match.StringPredicate;
import software.coley.recaf.services.search.query.DeclarationQuery;
import software.coley.recaf.services.search.result.Result;
import software.coley.recaf.services.search.result.Results;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class SearchDeclarationTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final SearchService searchService;

	public SearchDeclarationTool(WorkspaceManager workspaceManager, SearchService searchService) {
		this.workspaceManager = workspaceManager;
		this.searchService = searchService;
	}

	@Override
	public String name() { return "recaf_search_declarations"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Search declarations",
				"Search for field/method declarations by owner class, name, and/or descriptor",
				object(
						prop("owner", string("Owner class internal name (e.g. com/example/Foo)")),
						prop("name", string("Member name to search for")),
						prop("descriptor", string("Member descriptor")),
						prop("limit", integer("Max results to return (default 100, max 500)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String owner = str(args.get("owner"));
		String memberName = str(args.get("name"));
		String desc = str(args.get("descriptor"));
		int limit = clamp(integer(args.get("limit"), 100), 1, 500);

		if (owner == null && memberName == null && desc == null)
			return error("At least one of owner, name, or descriptor must be provided");

		StringPredicate ownerPred = owner != null ? new StringPredicate("eq", t -> t.equals(owner)) : null;
		StringPredicate namePred = memberName != null ? new StringPredicate("eq", t -> t.equals(memberName)) : null;
		StringPredicate descPred = desc != null ? new StringPredicate("eq", t -> t.equals(desc)) : null;

		try {
			Results results = searchService.search(ws, List.of(new DeclarationQuery(ownerPred, namePred, descPred)));

			JsonArray matches = new JsonArray();
			int count = 0;
			for (Result<?> r : results) {
				if (count >= limit) break;
				JsonObject match = new JsonObject();
				match.addProperty("declaration", r.toString());
				SearchStringTool.populatePath(match, r.getPath());
				matches.add(match);
				count++;
			}

			JsonObject out = new JsonObject();
			out.addProperty("totalFound", results.size());
			out.addProperty("returned", matches.size());
			out.add("matches", matches);
			return out;
		} catch (Exception e) {
			return error("Search failed: " + e.getMessage());
		}
	}
}

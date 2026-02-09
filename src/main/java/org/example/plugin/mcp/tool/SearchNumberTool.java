package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.search.match.NumberPredicate;
import software.coley.recaf.services.search.query.NumberQuery;
import software.coley.recaf.services.search.result.Result;
import software.coley.recaf.services.search.result.Results;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class SearchNumberTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final SearchService searchService;

	public SearchNumberTool(WorkspaceManager workspaceManager, SearchService searchService) {
		this.workspaceManager = workspaceManager;
		this.searchService = searchService;
	}

	@Override
	public String name() { return "recaf_search_number"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Search numbers",
				"Search for numeric constants in all classes",
				object(
						req("value", number("The number value to search for")),
						prop("limit", integer("Max results to return (default 100, max 500)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		double value = dbl(args.get("value"), Double.NaN);
		if (Double.isNaN(value)) return error("Missing or invalid value");

		int limit = clamp(integer(args.get("limit"), 100), 1, 500);

		NumberPredicate predicate = new NumberPredicate("equals", n -> n.doubleValue() == value);

		try {
			Results results = searchService.search(ws, List.of(new NumberQuery(predicate)));

			JsonArray matches = new JsonArray();
			int count = 0;
			for (Result<?> r : results) {
				if (count >= limit) break;
				JsonObject match = new JsonObject();
				match.addProperty("value", r.toString());
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

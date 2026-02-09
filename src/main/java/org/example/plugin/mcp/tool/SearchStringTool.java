package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.member.ClassMember;
import software.coley.recaf.path.PathNode;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.search.match.StringPredicate;
import software.coley.recaf.services.search.query.StringQuery;
import software.coley.recaf.services.search.result.Result;
import software.coley.recaf.services.search.result.Results;
import software.coley.recaf.services.search.result.StringResult;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import java.util.List;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class SearchStringTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final SearchService searchService;

	public SearchStringTool(WorkspaceManager workspaceManager, SearchService searchService) {
		this.workspaceManager = workspaceManager;
		this.searchService = searchService;
	}

	@Override
	public String name() { return "recaf_search_string"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Search strings",
				"Search for string constants in all classes. Supports exact, contains, prefix, suffix, and regex matching.",
				object(
						req("value", string("The string value to search for")),
						prop("mode", enumString("Match mode (default: contains)", "exact", "contains", "prefix", "suffix", "regex")),
						prop("limit", integer("Max results to return (default 100, max 500)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String value = str(args.get("value"));
		if (value == null || value.isEmpty()) return error("Missing value");

		String mode = str(args.get("mode"));
		if (mode == null) mode = "contains";
		int limit = clamp(integer(args.get("limit"), 100), 1, 500);

		StringPredicate predicate = buildPredicate(mode, value);
		if (predicate == null) return error("Invalid mode: " + mode);

		try {
			Results results = searchService.search(ws, List.of(new StringQuery(predicate)));

			JsonArray matches = new JsonArray();
			int count = 0;
			for (Result<?> r : results) {
				if (count >= limit) break;
				JsonObject match = new JsonObject();
				if (r instanceof StringResult sr) {
					match.addProperty("value", sr.toString());
				}
				populatePath(match, r.getPath());
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

	private static StringPredicate buildPredicate(String mode, String value) {
		return switch (mode) {
			case "exact" -> new StringPredicate("equals", t -> t.equals(value));
			case "contains" -> new StringPredicate("contains", t -> t.contains(value));
			case "prefix" -> new StringPredicate("prefix", t -> t.startsWith(value));
			case "suffix" -> new StringPredicate("suffix", t -> t.endsWith(value));
			case "regex" -> {
				var pattern = java.util.regex.Pattern.compile(value);
				yield new StringPredicate("regex", t -> pattern.matcher(t).find());
			}
			default -> null;
		};
	}

	static void populatePath(JsonObject target, PathNode<?> path) {
		if (path == null) return;
		ClassInfo classInfo = path.getValueOfType(ClassInfo.class);
		if (classInfo != null) {
			target.addProperty("className", classInfo.getName());
		}
		ClassMember member = path.getValueOfType(ClassMember.class);
		if (member != null) {
			target.addProperty("memberName", member.getName());
			target.addProperty("memberDesc", member.getDescriptor());
		}
	}
}

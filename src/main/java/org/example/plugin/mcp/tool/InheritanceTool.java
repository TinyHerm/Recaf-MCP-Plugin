package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.services.inheritance.InheritanceGraph;
import software.coley.recaf.services.inheritance.InheritanceGraphService;
import software.coley.recaf.services.inheritance.InheritanceVertex;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import java.util.Set;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class InheritanceTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final InheritanceGraphService inheritanceGraphService;

	public InheritanceTool(WorkspaceManager workspaceManager, InheritanceGraphService inheritanceGraphService) {
		this.workspaceManager = workspaceManager;
		this.inheritanceGraphService = inheritanceGraphService;
	}

	@Override
	public String name() { return "recaf_class_inheritance"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Class inheritance",
				"Get inheritance information for a class: parents (superclasses), children (subclasses), "
						+ "full family tree, and common parent between two classes",
				object(
						req("internalName", string("JVM internal class name, like com/example/Foo")),
						prop("secondClass", string("If provided, returns the common parent type between the two classes")),
						prop("includeObject", bool("Include java/lang/Object in family results (default false)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String name = str(args.get("internalName"));
		if (name == null || name.isBlank()) return error("Missing internalName");

		boolean includeObject = bool(args.get("includeObject"), false);

		InheritanceGraph graph = inheritanceGraphService.getOrCreateInheritanceGraph(ws);

		String secondClass = str(args.get("secondClass"));
		if (secondClass != null && !secondClass.isBlank()) {
			String common = graph.getCommon(name, secondClass);
			JsonObject out = new JsonObject();
			out.addProperty("firstClass", name);
			out.addProperty("secondClass", secondClass);
			out.addProperty("commonParent", common);
			out.addProperty("isAssignable", graph.isAssignableFrom(name, secondClass));
			return out;
		}

		InheritanceVertex vertex = graph.getVertex(name);
		if (vertex == null) return error("Class not found in inheritance graph: " + name);

		JsonObject out = new JsonObject();
		out.addProperty("className", name);
		out.addProperty("isLibrary", vertex.isLibraryVertex());

		JsonArray parents = new JsonArray();
		for (InheritanceVertex parent : vertex.getAllParents()) {
			parents.add(parent.getName());
		}
		out.add("parents", parents);

		JsonArray children = new JsonArray();
		for (InheritanceVertex child : vertex.getAllChildren()) {
			children.add(child.getName());
		}
		out.add("children", children);

		Set<InheritanceVertex> family = vertex.getFamily(includeObject);
		JsonArray familyArr = new JsonArray();
		for (InheritanceVertex f : family) {
			familyArr.add(f.getName());
		}
		out.add("family", familyArr);

		return out;
	}
}

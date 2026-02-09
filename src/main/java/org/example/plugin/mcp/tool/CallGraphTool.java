package org.example.plugin.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.callgraph.CallGraph;
import software.coley.recaf.services.callgraph.CallGraphService;
import software.coley.recaf.services.callgraph.ClassMethodsContainer;
import software.coley.recaf.services.callgraph.MethodVertex;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import java.util.Collection;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class CallGraphTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final CallGraphService callGraphService;

	public CallGraphTool(WorkspaceManager workspaceManager, CallGraphService callGraphService) {
		this.workspaceManager = workspaceManager;
		this.callGraphService = callGraphService;
	}

	@Override
	public String name() { return "recaf_method_callgraph"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Method call graph",
				"Get callers and callees of a specific method, or all methods in a class",
				object(
						req("internalName", string("JVM internal class name, like com/example/Foo")),
						prop("methodName", string("Method name (if omitted, returns all methods in the class)")),
						prop("methodDesc", string("Method descriptor (e.g. (I)V). Required if methodName is provided")),
						prop("limit", integer("Max callers/callees to return per method (default 50, max 200)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String className = str(args.get("internalName"));
		if (className == null || className.isBlank()) return error("Missing internalName");

		String methodName = str(args.get("methodName"));
		String methodDesc = str(args.get("methodDesc"));
		int limit = clamp(integer(args.get("limit"), 50), 1, 200);

		ClassPathNode path = ws.findJvmClass(className);
		if (path == null) return error("Class not found: " + className);

		JvmClassInfo classInfo = path.getValue().asJvmClass();
		CallGraph callGraph = callGraphService.getCurrentWorkspaceCallGraph();
		if (callGraph == null) return error("Call graph not available");

		ClassMethodsContainer container = callGraph.getClassMethodsContainer(classInfo);
		if (container == null) return error("No call graph data for class: " + className);

		if (methodName != null) {
			if (methodDesc == null) return error("methodDesc is required when methodName is provided");

			MethodVertex vertex = container.getVertex(methodName, methodDesc);
			if (vertex == null) return error("Method not found: " + methodName + methodDesc);

			JsonObject out = new JsonObject();
			out.addProperty("className", className);
			out.addProperty("methodName", methodName);
			out.addProperty("methodDesc", methodDesc);
			out.add("callers", buildVertexList(vertex.getCallers(), limit));
			out.add("callees", buildVertexList(vertex.getCalls(), limit));
			return out;
		}

		JsonArray methods = new JsonArray();
		for (MethodVertex vertex : container.getVertices()) {
			JsonObject mo = new JsonObject();
			mo.addProperty("name", vertex.getMethod().name());
			mo.addProperty("desc", vertex.getMethod().desc());
			mo.addProperty("callerCount", vertex.getCallers().size());
			mo.addProperty("calleeCount", vertex.getCalls().size());
			methods.add(mo);
		}

		JsonObject out = new JsonObject();
		out.addProperty("className", className);
		out.add("methods", methods);
		return out;
	}

	private static JsonArray buildVertexList(Collection<MethodVertex> vertices, int limit) {
		JsonArray arr = new JsonArray();
		int count = 0;
		for (MethodVertex v : vertices) {
			if (count >= limit) break;
			JsonObject vo = new JsonObject();
			vo.addProperty("owner", v.getMethod().owner());
			vo.addProperty("name", v.getMethod().name());
			vo.addProperty("desc", v.getMethod().desc());
			arr.add(vo);
			count++;
		}
		return arr;
	}
}

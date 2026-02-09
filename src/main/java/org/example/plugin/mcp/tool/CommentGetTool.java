package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.comment.ClassComments;
import software.coley.recaf.services.comment.CommentManager;
import software.coley.recaf.services.comment.WorkspaceComments;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class CommentGetTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final CommentManager commentManager;

	public CommentGetTool(WorkspaceManager workspaceManager, CommentManager commentManager) {
		this.workspaceManager = workspaceManager;
		this.commentManager = commentManager;
	}

	@Override
	public String name() { return "recaf_comment_get"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Get comment",
				"Get comments on a class, field, or method",
				object(
						req("internalName", string("JVM internal class name")),
						prop("memberName", string("Field or method name")),
						prop("memberDesc", string("Field or method descriptor (required with memberName)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		Workspace ws = workspaceManager.getCurrent();
		if (ws == null) return error("No workspace open");

		String className = str(args.get("internalName"));
		if (className == null || className.isBlank()) return error("Missing internalName");

		String memberName = str(args.get("memberName"));
		String memberDesc = str(args.get("memberDesc"));

		ClassPathNode classPath = ws.findJvmClass(className);
		if (classPath == null) return error("Class not found: " + className);

		WorkspaceComments wsComments = commentManager.getWorkspaceComments(ws);
		if (wsComments == null) {
			JsonObject out = new JsonObject();
			out.addProperty("comment", (String) null);
			return out;
		}

		ClassComments classComments = wsComments.getClassComments(classPath);
		if (classComments == null) {
			JsonObject out = new JsonObject();
			out.addProperty("comment", (String) null);
			return out;
		}

		String comment;
		if (memberName != null && memberDesc != null) {
			String fieldComment = classComments.getFieldComment(memberName, memberDesc);
			String methodComment = classComments.getMethodComment(memberName, memberDesc);
			comment = fieldComment != null ? fieldComment : methodComment;
		} else {
			comment = classComments.getClassComment();
		}

		JsonObject out = new JsonObject();
		out.addProperty("className", className);
		if (memberName != null) out.addProperty("memberName", memberName);
		if (memberDesc != null) out.addProperty("memberDesc", memberDesc);
		out.addProperty("comment", comment);
		return out;
	}
}

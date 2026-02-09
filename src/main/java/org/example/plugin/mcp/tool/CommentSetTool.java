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

public final class CommentSetTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final CommentManager commentManager;

	public CommentSetTool(WorkspaceManager workspaceManager, CommentManager commentManager) {
		this.workspaceManager = workspaceManager;
		this.commentManager = commentManager;
	}

	@Override
	public String name() { return "recaf_comment_set"; }

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Set comment",
				"Set or update a comment on a class, field, or method. Use empty string to delete.",
				object(
						req("internalName", string("JVM internal class name")),
						req("comment", string("Comment text (empty string to remove)")),
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

		String comment = str(args.get("comment"));
		if (comment == null) return error("Missing comment");

		String memberName = str(args.get("memberName"));
		String memberDesc = str(args.get("memberDesc"));

		ClassPathNode classPath = ws.findJvmClass(className);
		if (classPath == null) return error("Class not found: " + className);

		WorkspaceComments wsComments = commentManager.getOrCreateWorkspaceComments(ws);
		ClassComments classComments = wsComments.getOrCreateClassComments(classPath);

		if (memberName != null && memberDesc != null) {
			if (comment.isEmpty()) {
				classComments.setFieldComment(memberName, memberDesc, null);
				classComments.setMethodComment(memberName, memberDesc, null);
			} else {
				boolean isMethod = memberDesc.startsWith("(");
				if (isMethod) {
					classComments.setMethodComment(memberName, memberDesc, comment);
				} else {
					classComments.setFieldComment(memberName, memberDesc, comment);
				}
			}
		} else {
			classComments.setClassComment(comment.isEmpty() ? null : comment);
		}

		JsonObject out = new JsonObject();
		out.addProperty("className", className);
		out.addProperty("applied", true);
		return out;
	}
}

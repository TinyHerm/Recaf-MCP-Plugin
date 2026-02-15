package org.example.plugin.mcp.tool;

import com.google.gson.JsonObject;
import jakarta.enterprise.inject.Instance;
import software.coley.recaf.path.ClassMemberPathNode;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.model.Workspace;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.example.plugin.mcp.util.JsonUtil.*;
import static org.example.plugin.mcp.util.SchemaBuilder.*;

public final class OpenClassTool implements McpTool {

	private final WorkspaceManager workspaceManager;
	private final Instance<Actions> actions;

	public OpenClassTool(WorkspaceManager workspaceManager, Instance<Actions> actions) {
		this.workspaceManager = workspaceManager;
		this.actions = actions;
	}

	@Override
	public String name() {
		return "recaf_ui_open_class";
	}

	@Override
	public JsonObject schema() {
		return toolDef(name(), "Open class in UI",
				"Open a class in the Recaf UI. Optionally navigate to a specific member.",
				object(
						req("internalName", string("JVM internal class name, like com/example/Foo")),
						prop("memberName", string("Field or method name to navigate to")),
						prop("memberDesc", string("Field or method descriptor (e.g. (I)V or Ljava/lang/String;)"))
				));
	}

	@Override
	public JsonObject execute(JsonObject args) {
		if (!workspaceManager.hasCurrentWorkspace()) return error("No workspace open");
		Workspace ws = workspaceManager.getCurrent();

		String className = str(args.get("internalName"));
		if (className == null || className.isBlank()) return error("Missing internalName");

		ClassPathNode classPath = ws.findClass(className);
		if (classPath == null) return error("Class not found: " + className);

		String memberName = str(args.get("memberName"));
		String memberDesc = str(args.get("memberDesc"));

		ClassMemberPathNode memberPath = null;
		if (memberName != null && !memberName.isBlank()
				&& memberDesc != null && !memberDesc.isBlank()) {
			memberPath = classPath.child(memberName, memberDesc);
			if (memberPath == null)
				return error("Member not found: " + memberName + " " + memberDesc + " in " + className);
		}

		Actions actionsInstance;
		try {
			actionsInstance = actions.get();
		} catch (Exception e) {
			return error("UI not available: " + e.getMessage());
		}

		final ClassMemberPathNode mp = memberPath;
		CompletableFuture<String> result = new CompletableFuture<>();
		FxThreadUtil.run(() -> {
			try {
				if (mp != null) {
					actionsInstance.gotoDeclaration(mp);
					result.complete("Opened " + className + " at " + memberName + memberDesc);
				} else {
					actionsInstance.gotoDeclaration(classPath);
					result.complete("Opened " + className);
				}
			} catch (Exception e) {
				result.complete("Failed to open: " + e.getMessage());
			}
		});

		try {
			String message = result.get(5, TimeUnit.SECONDS);
			JsonObject out = new JsonObject();
			out.addProperty("opened", true);
			out.addProperty("className", className);
			if (mp != null) {
				out.addProperty("memberName", memberName);
				out.addProperty("memberDesc", memberDesc);
			}
			out.addProperty("message", message);
			return out;
		} catch (Exception e) {
			return error("Timed out waiting for UI: " + e.getMessage());
		}
	}
}

package org.example.plugin;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.example.plugin.mcp.McpServer;
import org.example.plugin.ui.McpStatusPane;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.plugin.Plugin;
import software.coley.recaf.plugin.PluginInformation;
import software.coley.recaf.services.assembler.AssemblerPipelineManager;
import software.coley.recaf.services.callgraph.CallGraphService;
import software.coley.recaf.services.comment.CommentManager;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.inheritance.InheritanceGraphService;
import software.coley.recaf.services.mapping.MappingApplierService;
import software.coley.recaf.services.mapping.gen.MappingGenerator;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.ui.docking.DockingManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.bentofx.dockable.Dockable;
import software.coley.bentofx.layout.DockContainer;
import software.coley.bentofx.layout.container.DockContainerBranch;
import software.coley.bentofx.layout.container.DockContainerLeaf;
import software.coley.bentofx.path.DockContainerPath;

@Dependent
@PluginInformation(
		id = "org.example.recaf-mcp",
		version = "1.0.0",
		name = "Recaf MCP",
		description = "Expose Recaf workspace via MCP tools"
)
public class RecafMcpPlugin implements Plugin {
	private static final Logger logger = Logging.get(RecafMcpPlugin.class);
	private final McpServer mcpServer;
	private final Instance<DockingManager> dockingManager;
	private volatile Dockable mcpDockable;

	@Inject
	public RecafMcpPlugin(WorkspaceManager workspaceManager,
	                      DecompilerManager decompilerManager,
	                      MappingApplierService mappingApplierService,
	                      AssemblerPipelineManager assemblerPipelineManager,
	                      SearchService searchService,
	                      CallGraphService callGraphService,
	                      InheritanceGraphService inheritanceGraphService,
	                      CommentManager commentManager,
	                      MappingGenerator mappingGenerator,
	                      Instance<Actions> actions,
	                      Instance<DockingManager> dockingManager) {
		this.mcpServer = new McpServer(workspaceManager, decompilerManager, mappingApplierService,
				assemblerPipelineManager, searchService, callGraphService, inheritanceGraphService,
				commentManager, mappingGenerator, actions);
		this.dockingManager = dockingManager;
	}

	@Override
	public void onEnable() {
		try {
			mcpServer.start();
			logger.info("MCP server listening on {}", mcpServer.getEndpoint());
		} catch (Exception e) {
			logger.error("Failed to start MCP server: {}", e.getMessage(), e);
			return;
		}
		FxThreadUtil.run(() -> tryOpenUi(0));
	}

	private void tryOpenUi(int attempt) {
		if (attempt >= 40) {
			logger.warn("Failed to open MCP status UI: docking containers not ready");
			return;
		}

		try {
			DockingManager dm = dockingManager.get();
			DockContainerPath containerPath = dm.getBento().search()
					.container(DockingManager.ID_CONTAINER_ROOT_BOTTOM);
			if (containerPath == null) {
				FxThreadUtil.delayedRun(250, () -> tryOpenUi(attempt + 1));
				return;
			}

			DockContainer container = containerPath.tailContainer();
			McpStatusPane pane = new McpStatusPane(mcpServer.getEndpoint(), mcpServer.getStats());
			Dockable dockable = dm.newDockable("Recaf MCP", d -> null, pane);
			dockable.setClosable(false);
			dockable.setDragGroupMask(DockingManager.GROUP_TOOLS);
			this.mcpDockable = dockable;

			boolean added;
			if (container instanceof DockContainerLeaf leaf) {
				Dockable previouslySelected = leaf.getSelectedDockable();
				added = leaf.addDockable(dockable);
				if (previouslySelected != null)
					leaf.selectDockable(previouslySelected);
			} else if (container instanceof DockContainerBranch branch) {
				added = branch.addDockable(dockable);
			} else {
				added = false;
			}

			if (!added)
				logger.warn("Failed to open MCP status UI: could not add dockable");
		} catch (Throwable t) {
			FxThreadUtil.delayedRun(250, () -> tryOpenUi(attempt + 1));
		}
	}

	@Override
	public void onDisable() {
		FxThreadUtil.run(() -> {
			try {
				DockingManager dm = dockingManager.get();
				Dockable d = mcpDockable;
				mcpDockable = null;
				if (d != null)
					d.inContainer(leaf -> leaf.closeDockable(d));
			} catch (Throwable t) {
				logger.debug("Failed to close MCP UI: {}", t.getMessage(), t);
			}
		});
		mcpServer.close();
	}
}

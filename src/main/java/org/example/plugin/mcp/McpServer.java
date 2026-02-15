package org.example.plugin.mcp;

import jakarta.enterprise.inject.Instance;
import org.example.plugin.mcp.tool.*;
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

import java.io.Closeable;
import java.io.IOException;

public final class McpServer implements Closeable {

	private static final int PORT = 33333;
	private final McpHttpServer httpServer;
	private final McpToolStats stats;
	private final String endpoint;

	public McpServer(WorkspaceManager workspaceManager,
	                 DecompilerManager decompilerManager,
	                 MappingApplierService mappingApplierService,
	                 AssemblerPipelineManager assemblerPipelineManager,
	                 SearchService searchService,
	                 CallGraphService callGraphService,
	                 InheritanceGraphService inheritanceGraphService,
	                 CommentManager commentManager,
	                 MappingGenerator mappingGenerator,
	                 Instance<Actions> actions) {
		McpProtocolHandler protocol = new McpProtocolHandler();
		this.stats = new McpToolStats();
		protocol.setStats(stats);

		protocol.registerTool(new WorkspaceStatusTool(workspaceManager));
		protocol.registerTool(new ListClassesTool(workspaceManager));
		protocol.registerTool(new ListFilesTool(workspaceManager));
		protocol.registerTool(new ClassOutlineTool(workspaceManager));
		protocol.registerTool(new ClassInfoTool(workspaceManager));
		protocol.registerTool(new DecompileTool(workspaceManager, decompilerManager));
		protocol.registerTool(new DisassembleTool(workspaceManager, assemblerPipelineManager));
		protocol.registerTool(new MethodDisassembleTool(workspaceManager, assemblerPipelineManager));
		protocol.registerTool(new AssembleTool(workspaceManager, assemblerPipelineManager));
		protocol.registerTool(new MethodBytecodeTool(workspaceManager));
		protocol.registerTool(new MappingTool(workspaceManager, mappingApplierService));
		protocol.registerTool(new GenerativeMappingTool(workspaceManager, mappingGenerator,
				mappingApplierService, inheritanceGraphService));
		protocol.registerTool(new ReadFileTool(workspaceManager));
		protocol.registerTool(new SearchStringTool(workspaceManager, searchService));
		protocol.registerTool(new SearchNumberTool(workspaceManager, searchService));
		protocol.registerTool(new SearchReferenceTool(workspaceManager, searchService));
		protocol.registerTool(new SearchDeclarationTool(workspaceManager, searchService));
		protocol.registerTool(new CallGraphTool(workspaceManager, callGraphService));
		protocol.registerTool(new InheritanceTool(workspaceManager, inheritanceGraphService));
		protocol.registerTool(new CommentGetTool(workspaceManager, commentManager));
		protocol.registerTool(new CommentSetTool(workspaceManager, commentManager));
		protocol.registerTool(new ObfuscationCheckTool(workspaceManager));
		protocol.registerTool(new MalwareCheckTool(workspaceManager));
		protocol.registerTool(new OpenClassTool(workspaceManager, actions));
		protocol.registerTool(new OpenFileTool(workspaceManager, actions));

		this.httpServer = new McpHttpServer(PORT, protocol::handle);
		this.endpoint = "http://127.0.0.1:" + PORT + "/mcp";
	}

	public void start() throws IOException {
		httpServer.start();
	}

	public String getEndpoint() {
		return endpoint;
	}

	public McpToolStats getStats() {
		return stats;
	}

	@Override
	public void close() {
		httpServer.close();
	}
}

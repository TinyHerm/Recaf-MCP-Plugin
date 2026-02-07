package org.example.plugin.mcp;

import org.example.plugin.mcp.tool.*;
import software.coley.recaf.services.assembler.AssemblerPipelineManager;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.mapping.MappingApplierService;
import software.coley.recaf.services.workspace.WorkspaceManager;

import java.io.Closeable;
import java.io.IOException;

public final class McpServer implements Closeable {

	private static final int PORT = 33333;
	private final McpHttpServer httpServer;
	private final String endpoint;

	public McpServer(WorkspaceManager workspaceManager,
	                 DecompilerManager decompilerManager,
	                 MappingApplierService mappingApplierService,
	                 AssemblerPipelineManager assemblerPipelineManager) {

		McpProtocolHandler protocol = new McpProtocolHandler();
		protocol.registerTool(new WorkspaceStatusTool(workspaceManager));
		protocol.registerTool(new ListClassesTool(workspaceManager));
		protocol.registerTool(new ClassOutlineTool(workspaceManager));
		protocol.registerTool(new DecompileTool(workspaceManager, decompilerManager));
		protocol.registerTool(new DisassembleTool(workspaceManager, assemblerPipelineManager));
		protocol.registerTool(new AssembleTool(workspaceManager, assemblerPipelineManager));
		protocol.registerTool(new MappingTool(workspaceManager, mappingApplierService));

		this.httpServer = new McpHttpServer(PORT, protocol::handle);
		this.endpoint = "http://127.0.0.1:" + PORT + "/mcp";
	}

	public void start() throws IOException {
		httpServer.start();
	}

	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public void close() {
		httpServer.close();
	}
}

# Recaf MCP Plugin

Gradle-based Recaf `4.x` plugin that starts a local MCP (Model Context Protocol) server.

The server exposes a small set of tools to inspect/modify the currently open Recaf workspace (list classes, decompile, disassemble, assemble+apply, mappings).

## Requirements

- Recaf 4.x
- Java 22+ (this project uses a Gradle toolchain set to Java 22)

## Building

Build the plugin jar:

```bash
./gradlew build
```

This generates `build/libs/example-plugin-{VERSION}.jar`.

## Installing Into Recaf

1. Locate Recaf's `plugins` directory:
   - Windows: `%APPDATA%\Recaf\plugins`
   - Linux: `$HOME/Recaf/plugins`
2. Copy the jar from `build/libs/` into that folder.
3. Start Recaf and check the logs for MCP startup messages.

## Running Recaf From This Project

This project includes a `runRecaf` task that builds the plugin and launches Recaf with `build/libs` registered as a plugin directory.

```bash
./gradlew runRecaf
```

## Plugin Info

- The plugin entry point class is `src/main/java/org/example/plugin/RecafMcpPlugin.java`.
- The service entry is generated from `build.gradle` (`pluginMainClass`).

## MCP Server

- Transport: HTTP + JSON-RPC 2.0 (POST)
- Bind: loopback only (`127.0.0.1`)
- Endpoint: `http://127.0.0.1:33333/mcp`
- Auth: none

Tool names must use underscores (no dots).

### Tools

- `recaf_workspace_status`
- `recaf_workspace_list_classes`
- `recaf_class_get_outline`
- `recaf_class_decompile`
- `recaf_class_disassemble`
- `recaf_class_assemble_apply`
- `recaf_mapping_apply`

All class names use JVM internal format: `com/example/Foo`.

### Quick Test (curl)

Initialize:

```bash
curl -s -X POST "http://127.0.0.1:33333/mcp" \
  -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

List tools:

```bash
curl -s -X POST "http://127.0.0.1:33333/mcp" \
  -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

Call a tool (example: list classes):

```bash
curl -s -X POST "http://127.0.0.1:33333/mcp" \
  -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"recaf_workspace_list_classes","arguments":{"prefix":"com/example/","limit":50}}}'
```

Call a tool (example: decompile):

```bash
curl -s -X POST "http://127.0.0.1:33333/mcp" \
  -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"recaf_class_decompile","arguments":{"internalName":"com/example/Foo","timeout":5000}}}'
```

## Showcase
https://github.com/user-attachments/assets/e9de7c4d-b956-4424-bbff-19b1d0d69afe

https://github.com/user-attachments/assets/ce12f257-66ce-4e93-92de-3dace355d2ed

## Recaf Plugin Dev Docs

Recaf developer documentation: https://recaf.coley.software/dev/index.html

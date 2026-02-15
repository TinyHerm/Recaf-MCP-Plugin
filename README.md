# Recaf MCP Plugin

Gradle-based Recaf `4.x` plugin that starts a local MCP (Model Context Protocol) server.

The server exposes a small set of tools to inspect/modify the currently open Recaf workspace (list classes, decompile, disassemble, assemble+apply, mappings).

### Requirements

- Recaf 4.x
- Java 22+ (this project uses a Gradle toolchain set to Java 22)

### Building

Build the plugin jar:

```bash
./gradlew build
```

This generates `build/libs/example-plugin-{VERSION}.jar`.

### Installing Into Recaf

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

### Plugin Info

- The plugin entry point class is `src/main/java/org/example/plugin/RecafMcpPlugin.java`.
- The service entry is generated from `build.gradle` (`pluginMainClass`).


### MCP Transport

- Protocol: JSON-RPC 2.0 over HTTP `POST`
- Bind: loopback only (`127.0.0.1`)
- Endpoint: `http://127.0.0.1:33333/mcp`
- Auth: none (local only)

### Recaf GUI Panel

The plugin creates a `Recaf MCP` dockable in the bottom area.

- Shows endpoint with copy button
- Shows total calls and error count
- Shows latest obfuscation, malware, and mapping summaries
- Shows recent tool calls with durations
- Recent calls width is user-resizable
- Logging tab remains the default selected tab on startup

## Tool Catalog

All class names use JVM internal format (`com/example/Foo`).

### Workspace And Class Introspection

- `recaf_workspace_status`
- `recaf_workspace_list_classes`
- `recaf_workspace_list_files`
- `recaf_class_get_outline`
- `recaf_class_info`
- `recaf_file_read`

### Code View And Edit

- `recaf_class_decompile` (supports `decompiler`, optional single-method isolation with `methodName` + `methodDesc`)
- `recaf_class_disassemble`
- `recaf_method_disassemble`
- `recaf_method_bytecode` (JASM text)
- `recaf_class_assemble_apply`

### Mapping

- `recaf_mapping_apply` (explicit renames you provide)
- `recaf_mapping_generate` (auto-generate renames for likely obfuscated/random names)

Mapping tools return rename counts and change lists (`from -> to`) so MCP clients can show exactly what changed.

### Search And Graph Analysis

- `recaf_search_string`
- `recaf_search_number`
- `recaf_search_references`
- `recaf_search_declarations`
- `recaf_method_callgraph`
- `recaf_class_inheritance`

### Comments

- `recaf_comment_get`
- `recaf_comment_set`

### Security / Obfuscation Heuristics

- `recaf_check_obfuscation`
- `recaf_check_malware`

`recaf_check_malware` reports category plus exact detection location (`className`, `method`, match type, matched API/constant).

### UI Navigation

- `recaf_ui_open_class`
- `recaf_ui_open_file`
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

### Showcase
https://github.com/user-attachments/assets/e9de7c4d-b956-4424-bbff-19b1d0d69afe

https://github.com/user-attachments/assets/ce12f257-66ce-4e93-92de-3dace355d2ed

### Recaf Plugin Dev Docs

Recaf developer documentation: https://recaf.coley.software/dev/index.html

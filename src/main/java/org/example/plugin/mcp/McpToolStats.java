package org.example.plugin.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class McpToolStats {

	private static final DateTimeFormatter TIME_FMT =
			DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final int MAX_LOG_ENTRIES = 200;

	private final ObservableList<ToolCallEntry> callLog = FXCollections.observableArrayList();
	private final ReadOnlyIntegerWrapper totalCalls = new ReadOnlyIntegerWrapper(0);
	private final ReadOnlyIntegerWrapper errorCalls = new ReadOnlyIntegerWrapper(0);
	private final AtomicInteger totalCallsAtomic = new AtomicInteger();
	private final AtomicInteger errorCallsAtomic = new AtomicInteger();

	private volatile String lastObfuscationSummary = "";
	private volatile String lastMalwareSummary = "";
	private volatile String lastMappingSummary = "";

	public void recordCall(String toolName, JsonObject result, boolean isError, long durationMs) {
		int tc = totalCallsAtomic.incrementAndGet();
		int ec = isError ? errorCallsAtomic.incrementAndGet() : errorCallsAtomic.get();

		extractAnalysisSummary(toolName, result);

		String time = TIME_FMT.format(Instant.now());
		String status = isError ? "ERROR" : "OK";
		ToolCallEntry entry = new ToolCallEntry(time, toolName, status, durationMs);

		Platform.runLater(() -> {
			totalCalls.set(tc);
			errorCalls.set(ec);
			callLog.addFirst(entry);
			if (callLog.size() > MAX_LOG_ENTRIES)
				callLog.removeLast();
		});
	}

	private void extractAnalysisSummary(String toolName, JsonObject result) {
		if (result == null) return;

		JsonElement content = result.get("structuredContent");
		if (content == null || !content.isJsonObject()) return;
		JsonObject data = content.getAsJsonObject();

		switch (toolName) {
			case "recaf_check_obfuscation" -> {
				StringBuilder sb = new StringBuilder();
				appendConfidence(sb, data, "stringEncryption", "String encryption");
				appendConfidence(sb, data, "referenceEncryption", "Reference encryption");
				appendConfidence(sb, data, "constantEncryption", "Constant encryption");
				appendConfidence(sb, data, "flowObfuscation", "Flow obfuscation");
				appendConfidence(sb, data, "nameObfuscation", "Name obfuscation");
				lastObfuscationSummary = sb.toString();
			}
			case "recaf_check_malware" -> {
				int findings = intProp(data, "totalFindings");
				int scanned = intProp(data, "classesScanned");
				StringBuilder sb = new StringBuilder();
				sb.append(findings).append(" findings across ").append(scanned).append(" classes");
				appendFirstMalwareFinding(sb, data);
				lastMalwareSummary = sb.toString();
			}
			case "recaf_mapping_generate" -> {
				int classes = intProp(data, "classesRenamed");
				int fields = intProp(data, "fieldsRenamed");
				int methods = intProp(data, "methodsRenamed");
				boolean applied = data.has("applied") && data.get("applied").getAsBoolean();
				StringBuilder sb = new StringBuilder();
				sb.append(classes).append("C / ").append(fields).append("F / ").append(methods).append("M")
						.append(applied ? " (applied)" : " (preview)");
				appendMappingChanges(sb, data, "classChanges", null);
				appendMappingChanges(sb, data, "fieldChanges", "owner");
				appendMappingChanges(sb, data, "methodChanges", "owner");
				lastMappingSummary = sb.toString();
			}
			case "recaf_mapping_apply" -> {
				int classes = intProp(data, "classesRenamed");
				int fields = intProp(data, "fieldsRenamed");
				int methods = intProp(data, "methodsRenamed");
				StringBuilder sb = new StringBuilder();
				sb.append(classes).append("C / ").append(fields).append("F / ").append(methods).append("M (applied)");
				appendMappingChanges(sb, data, "classChanges", null);
				appendMappingChanges(sb, data, "fieldChanges", "owner");
				appendMappingChanges(sb, data, "methodChanges", "owner");
				lastMappingSummary = sb.toString();
			}
		}
	}

	private static void appendMappingChanges(StringBuilder sb, JsonObject data, String key, String ownerKey) {
		JsonElement el = data.get(key);
		if (el == null || !el.isJsonArray()) return;
		JsonArray arr = el.getAsJsonArray();
		int shown = 0;
		for (JsonElement item : arr) {
			if (shown >= 3) break;
			if (!item.isJsonObject()) continue;
			JsonObject ch = item.getAsJsonObject();
			String from = stringProp(ch, "from");
			String to = stringProp(ch, "to");
			if (from == null || to == null) continue;
			String owner = ownerKey == null ? null : stringProp(ch, ownerKey);
			sb.append("\n");
			if (owner != null) sb.append(owner).append("::");
			sb.append(from).append(" -> ").append(to);
			shown++;
		}
	}

	private static String stringProp(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) return null;
		try { return el.getAsString(); } catch (Exception e) { return null; }
	}

	private static void appendFirstMalwareFinding(StringBuilder sb, JsonObject data) {
		JsonElement categoriesEl = data.get("categories");
		if (categoriesEl == null || !categoriesEl.isJsonObject()) return;
		for (var catEntry : categoriesEl.getAsJsonObject().entrySet()) {
			JsonElement catVal = catEntry.getValue();
			if (!catVal.isJsonObject()) continue;
			JsonElement detailsEl = catVal.getAsJsonObject().get("details");
			if (detailsEl == null || !detailsEl.isJsonArray() || detailsEl.getAsJsonArray().isEmpty()) continue;
			JsonElement first = detailsEl.getAsJsonArray().get(0);
			if (!first.isJsonObject()) continue;
			JsonObject finding = first.getAsJsonObject();
			String type = stringProp(finding, "type");
			String className = stringProp(finding, "className");
			String method = stringProp(finding, "method");
			if (type == null || className == null || method == null) continue;
			sb.append("\n").append(type).append(": ").append(className).append("#").append(method);
			return;
		}
	}

	private static void appendConfidence(StringBuilder sb, JsonObject data, String key, String label) {
		JsonElement el = data.get(key);
		if (el == null || !el.isJsonObject()) return;
		String conf = el.getAsJsonObject().has("confidence")
				? el.getAsJsonObject().get("confidence").getAsString() : "?";
		if (sb.length() > 0) sb.append("\n");
		sb.append(label).append(": ").append(conf);
	}

	private static int intProp(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) return 0;
		try { return el.getAsInt(); } catch (Exception e) { return 0; }
	}

	public ObservableList<ToolCallEntry> getCallLog() { return callLog; }
	public ReadOnlyIntegerProperty totalCallsProperty() { return totalCalls.getReadOnlyProperty(); }
	public ReadOnlyIntegerProperty errorCallsProperty() { return errorCalls.getReadOnlyProperty(); }
	public String getLastObfuscationSummary() { return lastObfuscationSummary; }
	public String getLastMalwareSummary() { return lastMalwareSummary; }
	public String getLastMappingSummary() { return lastMappingSummary; }

	public record ToolCallEntry(String time, String toolName, String status, long durationMs) {
		@Override
		public String toString() {
			return time + "  " + status + "  " + durationMs + "ms  " + toolName;
		}
	}
}

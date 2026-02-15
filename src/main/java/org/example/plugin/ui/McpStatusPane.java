package org.example.plugin.ui;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.example.plugin.mcp.McpToolStats;
import org.example.plugin.mcp.McpToolStats.ToolCallEntry;

public final class McpStatusPane extends VBox {

	private final McpToolStats stats;
	private final Label obfuscationLabel;
	private final Label malwareLabel;
	private final Label mappingLabel;

	public McpStatusPane(String endpoint, McpToolStats stats) {
		this.stats = stats;
		setPadding(new Insets(10));
		setSpacing(8);

		Label title = new Label("Recaf MCP");
		title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

		HBox endpointRow = buildEndpointRow(endpoint);

		Label countersLabel = new Label();
		countersLabel.textProperty().bind(Bindings.createStringBinding(
				() -> "Calls: " + stats.totalCallsProperty().get()
						+ "  Errors: " + stats.errorCallsProperty().get(),
				stats.totalCallsProperty(), stats.errorCallsProperty()));
		countersLabel.setStyle("-fx-font-family: monospace;");

		Separator sep1 = new Separator();

		Label analysisTitle = new Label("Analysis");
		analysisTitle.setStyle("-fx-font-weight: bold;");

		obfuscationLabel = new Label("Obfuscation: -");
		obfuscationLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
		obfuscationLabel.setWrapText(true);

		malwareLabel = new Label("Malware: -");
		malwareLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
		malwareLabel.setWrapText(true);

		mappingLabel = new Label("Mappings: -");
		mappingLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
		mappingLabel.setWrapText(true);

		Label logTitle = new Label("Recent calls");
		logTitle.setStyle("-fx-font-weight: bold;");

		ListView<ToolCallEntry> logView = new ListView<>(stats.getCallLog());
		logView.setStyle("-fx-border-color: transparent; -fx-background-insets: 0;");
		logView.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(ToolCallEntry item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(item.toString());
					setStyle("ERROR".equals(item.status())
							? "-fx-text-fill: #e06060; -fx-font-family: monospace; -fx-font-size: 11px;"
							: "-fx-font-family: monospace; -fx-font-size: 11px;");
				}
			}
		});
		logView.setFixedCellSize(22);

		VBox analysisBox = new VBox(6, analysisTitle, obfuscationLabel, malwareLabel, mappingLabel);
		analysisBox.setFillWidth(true);
		VBox.setVgrow(analysisBox, Priority.ALWAYS);

		VBox callsBox = new VBox(6, logTitle, logView);
		callsBox.setFillWidth(true);
		callsBox.setMinWidth(320);
		callsBox.setPadding(new Insets(0, 0, 0, 6));
		callsBox.setStyle("-fx-border-color: transparent;");
		VBox.setVgrow(logView, Priority.ALWAYS);

		SplitPane contentRow = new SplitPane(analysisBox, callsBox);
		contentRow.setDividerPositions(0.58);
		contentRow.setStyle("-fx-box-border: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
		VBox.setVgrow(contentRow, Priority.ALWAYS);

		stats.getCallLog().addListener((ListChangeListener<ToolCallEntry>) c -> refreshAnalysis());
		refreshAnalysis();

		getChildren().addAll(
				title, endpointRow, countersLabel,
				sep1, contentRow
		);
	}

	private HBox buildEndpointRow(String endpoint) {
		TextField endpointField = new TextField(endpoint);
		endpointField.setEditable(false);
		endpointField.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
		HBox.setHgrow(endpointField, Priority.ALWAYS);

		Button copyBtn = new Button("Copy");
		copyBtn.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(endpoint);
			Clipboard.getSystemClipboard().setContent(content);
		});

		HBox row = new HBox(6, endpointField, copyBtn);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void refreshAnalysis() {
		String obf = stats.getLastObfuscationSummary();
		obfuscationLabel.setText(obf.isEmpty() ? "Obfuscation: -" : "Obfuscation:\n" + obf);

		String mal = stats.getLastMalwareSummary();
		malwareLabel.setText(mal.isEmpty() ? "Malware: -" : "Malware:\n" + mal);

		String map = stats.getLastMappingSummary();
		mappingLabel.setText(map.isEmpty() ? "Mappings: -" : "Mappings:\n" + map);
	}
}

package org.example.plugin.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class McpStatusPane extends VBox {

	public McpStatusPane(String endpoint) {
		setPadding(new Insets(12));
		setSpacing(10);

		Label title = new Label("Recaf MCP");
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		TextField endpointField = new TextField(endpoint);
		endpointField.setEditable(false);
		HBox.setHgrow(endpointField, Priority.ALWAYS);

		Button copyBtn = new Button("Copy");
		copyBtn.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(endpoint);
			Clipboard.getSystemClipboard().setContent(content);
		});

		HBox endpointRow = new HBox(8, new Label("Endpoint"), endpointField, copyBtn);
		endpointRow.setFillHeight(true);

		Label hint = new Label("Loopback only - no authentication required");
		hint.setStyle("-fx-opacity: 0.75;");

		getChildren().addAll(title, endpointRow, hint);
	}
}

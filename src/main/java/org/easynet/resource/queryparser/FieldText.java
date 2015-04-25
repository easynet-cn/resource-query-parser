package org.easynet.resource.queryparser;

public class FieldText {
	private String field;
	private String text;

	public FieldText(String field, String text) {
		this.field = field;
		this.text = text;
	}

	public String getField() {
		return field;
	}

	public String getText() {
		return text;
	}
}

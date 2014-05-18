package org.easynet.resource.queryparser;

import java.util.Map;
import java.util.Set;

public interface FieldQueryTextStore {
	public void add(String field, String queryText);

	public Map<String, Set<String>> get();

	public Set<String> getFiedQueryText(String field);

	public Set<String> getQueryTexts();
}

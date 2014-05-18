package org.easynet.resource.queryparser;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class QueryParserFieldQueryTextStore implements FieldQueryTextStore {
	Map<String, Set<String>> store = new HashMap<String, Set<String>>();

	@Override
	public void add(String field, String queryText) {
		if (null != field && !store.containsKey(field)) {
			store.put(field, new LinkedHashSet<String>());
		}

		if (null != queryText && !queryText.isEmpty()) {
			store.get(field).add(queryText);
		}

	}

	@Override
	public Map<String, Set<String>> get() {
		return store;
	}

	@Override
	public Set<String> getFiedQueryText(String field) {
		return store.get(field);
	}

	@Override
	public Set<String> getQueryTexts() {
		Set<String> queryTexts = new LinkedHashSet<String>();

		for (Set<String> set : store.values()) {
			queryTexts.addAll(set);
		}

		return queryTexts;
	}

}

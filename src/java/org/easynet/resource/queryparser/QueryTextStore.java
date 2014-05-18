package org.easynet.resource.queryparser;

import java.util.LinkedHashSet;
import java.util.Set;

public class QueryTextStore implements QueryStore<String> {
	private Set<String> store = new LinkedHashSet<String>();

	@Override
	public void add(String queryText) {
		if (null != queryText && !queryText.isEmpty()) {
			store.add(queryText);
		}
	}

	@Override
	public Set<String> get() {
		return store;
	}

}

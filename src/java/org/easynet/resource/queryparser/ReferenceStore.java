package org.easynet.resource.queryparser;

import java.util.LinkedHashSet;
import java.util.Set;

public class ReferenceStore implements QueryStore<String> {
	private Set<String> store = new LinkedHashSet<String>();

	@Override
	public void add(String obj) {
		if (null != obj && !obj.isEmpty()) {
			store.add(obj);
		}

	}

	@Override
	public Set<String> get() {
		return store;
	}

}

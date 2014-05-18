package org.easynet.resource.queryparser;

import java.util.Set;

public interface QueryStore<T> {
	public void add(T obj);
	
	public Set<T> get();
}

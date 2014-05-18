package org.easynet.resource.queryparser;

import java.util.Collection;

import org.apache.lucene.search.Query;

public interface ReferenceQueryProvider {
	public void addQuery(String query);

	public Query getQuery(Token token);

	public Collection<String> getQueries();

	public String getRealQuery(String query);
}

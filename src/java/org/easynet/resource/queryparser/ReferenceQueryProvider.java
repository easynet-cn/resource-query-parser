package org.easynet.resource.queryparser;

import org.apache.lucene.search.Query;

public interface ReferenceQueryProvider {
	public void addQuery(String query);

	public Query getQuery(Token token);

	public String[] getQueries();

	public String getRealQuery(String query);
}

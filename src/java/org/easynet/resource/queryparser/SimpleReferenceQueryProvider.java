package org.easynet.resource.queryparser;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

public class SimpleReferenceQueryProvider implements ReferenceQueryProvider {
	List<String> queries = new ArrayList<String>();

	@Override
	public void addQuery(String query) {
		if (null != query && !query.isEmpty()) {
			queries.add(query);
		}
	}

	@Override
	public Query getQuery(Token token) {
		QueryParser queryPaser = new QueryParser(Version.LUCENE_47, null,
				new StandardAnalyzer(Version.LUCENE_47));
		try {
			return queryPaser.parse(queries.get(Integer.parseInt(token.image
					.substring(1)) - 1));
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
}
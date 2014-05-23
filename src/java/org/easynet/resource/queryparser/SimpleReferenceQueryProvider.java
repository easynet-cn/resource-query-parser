package org.easynet.resource.queryparser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

public class SimpleReferenceQueryProvider implements ReferenceQueryProvider {
	List<String> queries = new ArrayList<String>();
	Pattern pattern = Pattern.compile("#[\\d]+");

	Analyzer analyzer = null;
	FieldQueryTextStore fieldQueryTextStore = null;
	QueryStore<String> referenceStore = null;

	public SimpleReferenceQueryProvider(Analyzer analyzer,
			FieldQueryTextStore fieldQueryTextStore,
			QueryStore<String> referenceStore) {
		this.analyzer = analyzer;
		this.fieldQueryTextStore = fieldQueryTextStore;
		this.referenceStore = referenceStore;
	}

	@Override
	public void addQuery(String query) {
		if (null != query && !query.isEmpty()) {
			queries.add(query);
		}
	}

	@Override
	public Query getQuery(Token token) {
		QueryParser queryPaser = new QueryParser(Version.LUCENE_48, null,
				analyzer);
		queryPaser.setFieldQueryTextStore(fieldQueryTextStore);
		queryPaser.setReferenceStore(referenceStore);
		queryPaser.setReferenceQueryProvider(this);

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

	public String[] getQueries() {
		return (String[]) queries.toArray(new String[0]);
	}

	public String getRealQuery(String query) {
		if (isReferenceQuery(query) == false || queries.isEmpty()) {
			return query;
		} else {
			Matcher m = pattern.matcher(query);
			StringBuffer sb = new StringBuffer();

			while (m.find()) {
				String ref = m.group(0);

				m.appendReplacement(sb, Matcher
						.quoteReplacement(getRealQuery(queries.get(Integer
								.parseInt(ref.substring(1)) - 1))));
			}

			return sb.toString();
		}
	}

	boolean isReferenceQuery(String query) {
		if (null == query || query.isEmpty()) {
			return false;
		} else {
			return pattern.matcher(query).find();
		}
	}
}

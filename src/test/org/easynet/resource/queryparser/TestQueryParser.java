package org.easynet.resource.queryparser;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.easynet.resource.queryparser.QueryParser.Operator;
import org.junit.Test;

/**
 * Tests QueryParser.
 */
public class TestQueryParser extends QueryParserTestBase {

	public static class QPTestParser extends QueryParser {
		public QPTestParser(String f, Analyzer a) {
			super(TEST_VERSION_CURRENT, f, a);
		}

		@Override
		protected Query getFuzzyQuery(String field, String termStr,
				float minSimilarity) throws ParseException {
			throw new ParseException("Fuzzy queries not allowed");
		}

		@Override
		protected Query getWildcardQuery(String field, String termStr)
				throws ParseException {
			throw new ParseException("Wildcard queries not allowed");
		}
	}

	public QueryParser getParser(Analyzer a) throws Exception {
		if (a == null)
			a = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT,
				getDefaultField(), a);
		qp.setDefaultOperator(QueryParserBase.OR_OPERATOR);
		return qp;
	}

	public QueryParser getParserConfig(Analyzer a) throws Exception {
		return getParser(a);
	}

	public Query getQuery(String query, QueryParser cqpC) throws Exception {
		assert cqpC != null : "Parameter must not be null";
		assert (cqpC instanceof QueryParser) : "Parameter must be instance of QueryParser";
		QueryParser qp = (QueryParser) cqpC;
		return qp.parse(query);
	}

	@Override
	public Query getQuery(String query, Analyzer a) throws Exception {
		return getParser(a).parse(query);
	}

	@Override
	public boolean isQueryParserException(Exception exception) {
		return exception instanceof ParseException;
	}

	public void setDefaultOperatorOR(QueryParser cqpC) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDefaultOperator(Operator.OR);
	}

	public void setDefaultOperatorAND(QueryParser cqpC) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDefaultOperator(Operator.AND);
	}

	public void setAnalyzeRangeTerms(QueryParser cqpC, boolean value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setAnalyzeRangeTerms(value);
	}

	public void setAutoGeneratePhraseQueries(QueryParser cqpC, boolean value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setAutoGeneratePhraseQueries(value);
	}

	public void setDateResolution(QueryParser cqpC, CharSequence field,
			Resolution value) {
		assert (cqpC instanceof QueryParser);
		QueryParser qp = (QueryParser) cqpC;
		qp.setDateResolution(field.toString(), value);
	}

	@Override
	@Test
	public void testDefaultOperator() throws Exception {
		QueryParser qp = getParser(new MockAnalyzer(random()));
		// make sure OR is the default:
		assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
		setDefaultOperatorAND(qp);
		assertEquals(QueryParserBase.AND_OPERATOR, qp.getDefaultOperator());
		setDefaultOperatorOR(qp);
		assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
	}

	@Override
	@Test
	public void testStarParsing() throws Exception {
		final int[] type = new int[1];
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)) {
			@Override
			protected Query getWildcardQuery(String field, String termStr) {
				// override error checking of superclass
				type[0] = 1;
				return new TermQuery(new Term(field, termStr));
			}

			@Override
			protected Query getPrefixQuery(String field, String termStr) {
				// override error checking of superclass
				type[0] = 2;
				return new TermQuery(new Term(field, termStr));
			}

			@Override
			protected Query getFieldQuery(String field, String queryText,
					boolean quoted) throws ParseException {
				type[0] = 3;
				return super.getFieldQuery(field, queryText, quoted);
			}
		};

		TermQuery tq;

		tq = (TermQuery) qp.parse("foo=zoo*");
		assertEquals("zoo", tq.getTerm().text());
		assertEquals(2, type[0]);

		tq = (TermQuery) qp.parse("foo=zoo*^2");
		assertEquals("zoo", tq.getTerm().text());
		assertEquals(2, type[0]);
		assertEquals(tq.getBoost(), 2, 0);

		tq = (TermQuery) qp.parse("foo=*");
		assertEquals("*", tq.getTerm().text());
		assertEquals(1, type[0]); // could be a valid prefix query in the future
									// too

		tq = (TermQuery) qp.parse("foo=*^2");
		assertEquals("*", tq.getTerm().text());
		assertEquals(1, type[0]);
		assertEquals(tq.getBoost(), 2, 0);

		tq = (TermQuery) qp.parse("*=foo");
		assertEquals("*", tq.getTerm().field());
		assertEquals("foo", tq.getTerm().text());
		assertEquals(3, type[0]);

		tq = (TermQuery) qp.parse("*=*");
		assertEquals("*", tq.getTerm().field());
		assertEquals("*", tq.getTerm().text());
		assertEquals(1, type[0]); // could be handled as a prefix query in the
									// future

		tq = (TermQuery) qp.parse("(*=*)");
		assertEquals("*", tq.getTerm().field());
		assertEquals("*", tq.getTerm().text());
		assertEquals(1, type[0]);

	}

	@Test
	public void testCustomQueryParserWildcard() {
		try {
			new QPTestParser("contents", new MockAnalyzer(random(),
					MockTokenizer.WHITESPACE, false)).parse("a?t");
			fail("Wildcard queries should not be allowed");
		} catch (ParseException expected) {
			// expected exception
		}
	}

	@Test
	public void testCustomQueryParserFuzzy() throws Exception {
		try {
			new QPTestParser("contents", new MockAnalyzer(random(),
					MockTokenizer.WHITESPACE, false)).parse("xunit~");
			fail("Fuzzy queries should not be allowed");
		} catch (ParseException expected) {
			// expected exception
		}
	}

	/** query parser that doesn't expand synonyms when users use double quotes */
	private class SmartQueryParser extends QueryParser {
		Analyzer morePrecise = new Analyzer2();

		public SmartQueryParser() {
			super(TEST_VERSION_CURRENT, "field", new Analyzer1());
		}

		@Override
		protected Query getFieldQuery(String field, String queryText,
				boolean quoted) throws ParseException {
			if (quoted)
				return newFieldQuery(morePrecise, field, queryText, quoted);
			else
				return super.getFieldQuery(field, queryText, quoted);
		}
	}

	@Override
	@Test
	public void testNewFieldQuery() throws Exception {
		/** ordinary behavior, synonyms form uncoordinated boolean query */
		QueryParser dumb = new QueryParser(TEST_VERSION_CURRENT, "field",
				new Analyzer1());
		BooleanQuery expanded = new BooleanQuery(true);
		expanded.add(new TermQuery(new Term("field", "dogs")),
				BooleanClause.Occur.SHOULD);
		expanded.add(new TermQuery(new Term("field", "dog")),
				BooleanClause.Occur.SHOULD);
		assertEquals(expanded, dumb.parse("\"dogs\""));
		/** even with the phrase operator the behavior is the same */
		assertEquals(expanded, dumb.parse("dogs"));

		/**
		 * custom behavior, the synonyms are expanded, unless you use quote
		 * operator
		 */
		QueryParser smart = new SmartQueryParser();
		assertEquals(expanded, smart.parse("dogs"));

		Query unexpanded = new TermQuery(new Term("field", "dogs"));
		assertEquals(unexpanded, smart.parse("\"dogs\""));
	}

	// TODO: fold these into QueryParserTestBase

	/** adds synonym of "dog" for "dogs". */
	static class MockSynonymAnalyzer extends Analyzer {
		@Override
		protected TokenStreamComponents createComponents(String fieldName,
				Reader reader) {
			MockTokenizer tokenizer = new MockTokenizer(reader);
			return new TokenStreamComponents(tokenizer, new MockSynonymFilter(
					tokenizer));
		}
	}

	/** simple synonyms test */
	@Test
	public void testSynonyms() throws Exception {
		BooleanQuery expected = new BooleanQuery(true);
		expected.add(new TermQuery(new Term("field", "dogs")),
				BooleanClause.Occur.SHOULD);
		expected.add(new TermQuery(new Term("field", "dog")),
				BooleanClause.Occur.SHOULD);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockSynonymAnalyzer());
		assertEquals(expected, qp.parse("dogs"));
		assertEquals(expected, qp.parse("\"dogs\""));
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("dogs"));
		assertEquals(expected, qp.parse("\"dogs\""));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("dogs^2"));
		assertEquals(expected, qp.parse("\"dogs\"^2"));
	}

	/** forms multiphrase query */
	@Test
	public void testSynonymsPhrase() throws Exception {
		MultiPhraseQuery expected = new MultiPhraseQuery();
		expected.add(new Term("field", "old"));
		expected.add(new Term[] { new Term("field", "dogs"),
				new Term("field", "dog") });
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockSynonymAnalyzer());
		assertEquals(expected, qp.parse("\"old dogs\""));
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("\"old dogs\""));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("\"old dogs\"^2"));
		expected.setSlop(3);
		assertEquals(expected, qp.parse("\"old dogs\"~3^2"));
	}

	/**
	 * adds synonym of "國" for "国".
	 */
	protected static class MockCJKSynonymFilter extends TokenFilter {
		CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
		PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
		boolean addSynonym = false;

		public MockCJKSynonymFilter(TokenStream input) {
			super(input);
		}

		@Override
		public final boolean incrementToken() throws IOException {
			if (addSynonym) { // inject our synonym
				clearAttributes();
				termAtt.setEmpty().append("國");
				posIncAtt.setPositionIncrement(0);
				addSynonym = false;
				return true;
			}

			if (input.incrementToken()) {
				addSynonym = termAtt.toString().equals("国");
				return true;
			} else {
				return false;
			}
		}
	}

	static class MockCJKSynonymAnalyzer extends Analyzer {
		@Override
		protected TokenStreamComponents createComponents(String fieldName,
				Reader reader) {
			Tokenizer tokenizer = new SimpleCJKTokenizer(reader);
			return new TokenStreamComponents(tokenizer,
					new MockCJKSynonymFilter(tokenizer));
		}
	}

	/** simple CJK synonym test */
	@Test
	public void testCJKSynonym() throws Exception {
		BooleanQuery expected = new BooleanQuery(true);
		expected.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		expected.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		assertEquals(expected, qp.parse("国"));
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("国"));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("国^2"));
	}

	/** synonyms with default OR operator */
	@Test
	public void testCJKSynonymsOR() throws Exception {
		BooleanQuery expected = new BooleanQuery();
		expected.add(new TermQuery(new Term("field", "中")),
				BooleanClause.Occur.SHOULD);
		BooleanQuery inner = new BooleanQuery(true);
		inner.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner, BooleanClause.Occur.SHOULD);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		assertEquals(expected, qp.parse("中国"));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("中国^2"));
	}

	/** more complex synonyms with default OR operator */
	@Test
	public void testCJKSynonymsOR2() throws Exception {
		BooleanQuery expected = new BooleanQuery();
		expected.add(new TermQuery(new Term("field", "中")),
				BooleanClause.Occur.SHOULD);
		BooleanQuery inner = new BooleanQuery(true);
		inner.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner, BooleanClause.Occur.SHOULD);
		BooleanQuery inner2 = new BooleanQuery(true);
		inner2.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner2.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner2, BooleanClause.Occur.SHOULD);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		assertEquals(expected, qp.parse("中国国"));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("中国国^2"));
	}

	/** synonyms with default AND operator */
	@Test
	public void testCJKSynonymsAND() throws Exception {
		BooleanQuery expected = new BooleanQuery();
		expected.add(new TermQuery(new Term("field", "中")),
				BooleanClause.Occur.MUST);
		BooleanQuery inner = new BooleanQuery(true);
		inner.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner, BooleanClause.Occur.MUST);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("中国"));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("中国^2"));
	}

	/** more complex synonyms with default AND operator */
	@Test
	public void testCJKSynonymsAND2() throws Exception {
		BooleanQuery expected = new BooleanQuery();
		expected.add(new TermQuery(new Term("field", "中")),
				BooleanClause.Occur.MUST);
		BooleanQuery inner = new BooleanQuery(true);
		inner.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner, BooleanClause.Occur.MUST);
		BooleanQuery inner2 = new BooleanQuery(true);
		inner2.add(new TermQuery(new Term("field", "国")),
				BooleanClause.Occur.SHOULD);
		inner2.add(new TermQuery(new Term("field", "國")),
				BooleanClause.Occur.SHOULD);
		expected.add(inner2, BooleanClause.Occur.MUST);
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("中国国"));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("中国国^2"));
	}

	/** forms multiphrase query */
	@Test
	public void testCJKSynonymsPhrase() throws Exception {
		MultiPhraseQuery expected = new MultiPhraseQuery();
		expected.add(new Term("field", "中"));
		expected.add(new Term[] { new Term("field", "国"),
				new Term("field", "國") });
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "field",
				new MockCJKSynonymAnalyzer());
		qp.setDefaultOperator(Operator.AND);
		assertEquals(expected, qp.parse("\"中国\""));
		expected.setBoost(2.0f);
		assertEquals(expected, qp.parse("\"中国\"^2"));
		expected.setSlop(3);
		assertEquals(expected, qp.parse("\"中国\"~3^2"));
	}

	@Test
	public void testIPCPrefixQuery() throws Exception {
		PrefixQuery expected = new PrefixQuery(new Term("ic", "g06f1/"));
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "ic",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));
		assertEquals(expected, qp.parse("g06f1/*"));
	}

	@Test(expected = MissingFieldException.class)
	public void testCheckField() throws ParseException {
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "all",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));
		qp.setCheckField(true);

		Set<String> fields = new HashSet<String>();

		fields.add("ti");

		qp.setFields(fields);

		qp.parse("ab=test");
	}

	@Test
	public void testFieldMap() throws Exception {
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "all",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));

		qp.setCheckField(true);

		Set<String> fields = new HashSet<String>();

		fields.add("ti");
		fields.add("ab");

		qp.setFields(fields);

		Map<String, String> fieldMap = new HashMap<String, String>();

		fieldMap.put("名称", "ti");
		fieldMap.put("摘要", "ab");

		Set<String> exceptedFields = new HashSet<String>();

		exceptedFields.add("ti");
		exceptedFields.add("ab");
		exceptedFields.add("名称");
		exceptedFields.add("摘要");

		qp.setFieldMap(fieldMap);

		assertEquals(exceptedFields, qp.getFields());

		assertEquals(qp.parse("ti=测试 and ab=测试"), qp.parse("名称=测试 and 摘要=测试"));

	}

	@Test
	public void testQuryTexts() throws Exception {
		Set<String> exceptedQueryTexts = new LinkedHashSet<String>();

		exceptedQueryTexts.add("测试");
		exceptedQueryTexts.add("cn");
		exceptedQueryTexts.add("中国");
		exceptedQueryTexts.add("test case");

		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "all",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));

		qp.parse("ti=测试 and ab=测试 OR co=(cn OR 中国^2) ti=\"test case\"");

		assertEquals(exceptedQueryTexts, qp.getQueryTextStore().get());
	}

	@Test
	public void testQueryTextsWithReferenceQuery() throws Exception {
		Set<String> exceptedQueryTexts = new LinkedHashSet<String>();

		exceptedQueryTexts.add("测试");
		exceptedQueryTexts.add("cn");
		exceptedQueryTexts.add("中国");
		exceptedQueryTexts.add("test case");
		exceptedQueryTexts.add("reference");

		QueryStore<String> queryTextStore = new QueryTextStore();
		QueryStore<String> referenceStore = new ReferenceStore();

		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "all",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));
		qp.setQueryTextStore(queryTextStore);

		SimpleReferenceQueryProvider referenceQueryProvider = new SimpleReferenceQueryProvider(
				queryTextStore, referenceStore);

		referenceQueryProvider.addQuery("ti=reference");

		qp.setReferenceQueryProvider(referenceQueryProvider);

		qp.parse("ti=测试 and ab=测试 OR co=(cn OR 中国^2) ti=\"test case\" and #1");

		assertEquals(exceptedQueryTexts, qp.getQueryTextStore().get());
	}

	public class SimpleReferenceQueryProvider implements ReferenceQueryProvider {
		List<String> queries = new ArrayList<String>();
		QueryStore<String> queryTextStore = null;
		QueryStore<String> referenceStore = null;

		public SimpleReferenceQueryProvider(QueryStore<String> queryTextStore,
				QueryStore<String> referenceStore) {
			this.queryTextStore = queryTextStore;
			this.referenceStore = referenceStore;
		}

		public void addQuery(String query) {
			queries.add(query);
		}

		@Override
		public Query getQuery(Token token) {
			QueryParser queryPaser = new QueryParser(TEST_VERSION_CURRENT,
					"all", new MockAnalyzer(random(), MockTokenizer.SIMPLE,
							false));
			queryPaser.setQueryTextStore(queryTextStore);
			queryPaser.setReferenceStore(referenceStore);
			queryPaser.setReferenceQueryProvider(this);

			try {
				return queryPaser.parse(queries.get(Integer
						.parseInt(token.image.substring(1)) - 1));
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

	@Test
	public void testReferenceQuery() throws Exception {
		QueryStore<String> queryTextStore = new QueryTextStore();
		QueryStore<String> referenceStore = new ReferenceStore();
		QueryParser qp = new QueryParser(TEST_VERSION_CURRENT, "all",
				new MockAnalyzer(random(), MockTokenizer.SIMPLE, false));
		qp.setReferenceStore(referenceStore);

		SimpleReferenceQueryProvider referenceQueryProvider = new SimpleReferenceQueryProvider(
				queryTextStore, referenceStore);

		referenceQueryProvider.addQuery("ti=reference");
		referenceQueryProvider.addQuery("i=test or #1");
		referenceQueryProvider.addQuery("#1 and #2");

		qp.setReferenceQueryProvider(referenceQueryProvider);

		Set<String> exceptedReference = new LinkedHashSet<String>();

		exceptedReference.add("#1");
		exceptedReference.add("#2");
		exceptedReference.add("#3");

		System.out.println(qp.parse("#1 and #2 and #3"));

		assertEquals(exceptedReference, referenceStore.get());
	}
}
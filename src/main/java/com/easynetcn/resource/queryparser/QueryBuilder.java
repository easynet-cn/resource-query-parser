package com.easynetcn.resource.queryparser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;

/**
 * Creates queries from the {@link Analyzer} chain.
 * <p>
 * Example usage:
 * 
 * <pre class="prettyprint">
 * QueryBuilder builder = new QueryBuilder(analyzer);
 * Query a = builder.createBooleanQuery("body", "just a test");
 * Query b = builder.createPhraseQuery("body", "another test");
 * Query c = builder.createMinShouldMatchQuery("body", "another test", 0.5f);
 * </pre>
 * <p>
 * This can also be used as a subclass for query parsers to make it easier to
 * interact with the analysis chain. Factory methods such as
 * {@code newTermQuery} are provided so that the generated queries can be
 * customized.
 */
public class QueryBuilder {
	private Analyzer analyzer;
	private boolean enablePositionIncrements = true;

	/** Creates a new QueryBuilder using the given analyzer. */
	public QueryBuilder(Analyzer analyzer) {
		this.analyzer = analyzer;
	}

	/**
	 * Creates a boolean query from the query text.
	 * <p>
	 * This is equivalent to
	 * {@code createBooleanQuery(field, queryText, Occur.SHOULD)}
	 * 
	 * @param field
	 *            field name
	 * @param queryText
	 *            text to be passed to the analyzer
	 * @return {@code TermQuery} or {@code BooleanQuery}, based on the analysis
	 *         of {@code queryText}
	 */
	public Query createBooleanQuery(String field, String queryText) {
		return createBooleanQuery(field, queryText, BooleanClause.Occur.SHOULD);
	}

	/**
	 * Creates a boolean query from the query text.
	 * <p>
	 * 
	 * @param field
	 *            field name
	 * @param queryText
	 *            text to be passed to the analyzer
	 * @param operator
	 *            operator used for clauses between analyzer tokens.
	 * @return {@code TermQuery} or {@code BooleanQuery}, based on the analysis
	 *         of {@code queryText}
	 */
	public Query createBooleanQuery(String field, String queryText, BooleanClause.Occur operator) {
		if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
			throw new IllegalArgumentException("invalid operator: only SHOULD or MUST are allowed");
		}
		return createFieldQuery(analyzer, operator, field, queryText, false, 0);
	}

	/**
	 * Creates a phrase query from the query text.
	 * <p>
	 * This is equivalent to {@code createPhraseQuery(field, queryText, 0)}
	 * 
	 * @param field
	 *            field name
	 * @param queryText
	 *            text to be passed to the analyzer
	 * @return {@code TermQuery}, {@code BooleanQuery}, {@code PhraseQuery}, or
	 *         {@code MultiPhraseQuery}, based on the analysis of
	 *         {@code queryText}
	 */
	public Query createPhraseQuery(String field, String queryText) {
		return createPhraseQuery(field, queryText, 0);
	}

	/**
	 * Creates a phrase query from the query text.
	 * <p>
	 * 
	 * @param field
	 *            field name
	 * @param queryText
	 *            text to be passed to the analyzer
	 * @param phraseSlop
	 *            number of other words permitted between words in query phrase
	 * @return {@code TermQuery}, {@code BooleanQuery}, {@code PhraseQuery}, or
	 *         {@code MultiPhraseQuery}, based on the analysis of
	 *         {@code queryText}
	 */
	public Query createPhraseQuery(String field, String queryText, int phraseSlop) {
		return createFieldQuery(analyzer, BooleanClause.Occur.MUST, field, queryText, true, phraseSlop);
	}

	/**
	 * Creates a minimum-should-match query from the query text.
	 * <p>
	 * 
	 * @param field
	 *            field name
	 * @param queryText
	 *            text to be passed to the analyzer
	 * @param fraction
	 *            of query terms {@code [0..1]} that should match
	 * @return {@code TermQuery} or {@code BooleanQuery}, based on the analysis
	 *         of {@code queryText}
	 */
	public Query createMinShouldMatchQuery(String field, String queryText, float fraction) {
		if (Float.isNaN(fraction) || fraction < 0 || fraction > 1) {
			throw new IllegalArgumentException("fraction should be >= 0 and <= 1");
		}

		// TODO: wierd that BQ equals/rewrite/scorer doesn't handle this?
		if (fraction == 1) {
			return createBooleanQuery(field, queryText, BooleanClause.Occur.MUST);
		}

		Query query = createFieldQuery(analyzer, BooleanClause.Occur.SHOULD, field, queryText, false, 0);
		if (query instanceof BooleanQuery) {
			BooleanQuery bq = (BooleanQuery) query;
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			builder.setDisableCoord(bq.isCoordDisabled());
			builder.setMinimumNumberShouldMatch((int) (fraction * bq.clauses().size()));
			for (BooleanClause clause : bq) {
				builder.add(clause);
			}
			query = builder.build();
		}
		return query;
	}

	/**
	 * Returns the analyzer.
	 * 
	 * @see #setAnalyzer(Analyzer)
	 */
	public Analyzer getAnalyzer() {
		return analyzer;
	}

	/**
	 * Sets the analyzer used to tokenize text.
	 */
	public void setAnalyzer(Analyzer analyzer) {
		this.analyzer = analyzer;
	}

	/**
	 * Returns true if position increments are enabled.
	 * 
	 * @see #setEnablePositionIncrements(boolean)
	 */
	public boolean getEnablePositionIncrements() {
		return enablePositionIncrements;
	}

	/**
	 * Set to <code>true</code> to enable position increments in result query.
	 * <p>
	 * When set, result phrase and multi-phrase queries will be aware of
	 * position increments. Useful when e.g. a StopFilter increases the position
	 * increment of the token that follows an omitted token.
	 * <p>
	 * Default: true.
	 */
	public void setEnablePositionIncrements(boolean enable) {
		this.enablePositionIncrements = enable;
	}

	/**
	 * Creates a query from the analysis chain.
	 * <p>
	 * Expert: this is more useful for subclasses such as queryparsers. If using
	 * this class directly, just use {@link #createBooleanQuery(String, String)}
	 * and {@link #createPhraseQuery(String, String)}
	 * 
	 * @param analyzer
	 *            analyzer used for this query
	 * @param operator
	 *            default boolean operator used for this query
	 * @param field
	 *            field to create queries against
	 * @param queryText
	 *            text to be passed to the analysis chain
	 * @param quoted
	 *            true if phrases should be generated when terms occur at more
	 *            than one position
	 * @param phraseSlop
	 *            slop factor for phrase/multiphrase queries
	 */
	protected final Query createFieldQuery(Analyzer analyzer, BooleanClause.Occur operator, String field,
			String queryText, boolean quoted, int phraseSlop) {
		assert operator == BooleanClause.Occur.SHOULD || operator == BooleanClause.Occur.MUST;

		// Use the analyzer to get all the tokens, and then build an appropriate
		// query based on the analysis chain.

		try (TokenStream source = analyzer.tokenStream(field, queryText);
				CachingTokenFilter stream = new CachingTokenFilter(source)) {

			TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
			PositionIncrementAttribute posIncAtt = stream.addAttribute(PositionIncrementAttribute.class);

			if (termAtt == null) {
				return null;
			}

			// phase 1: read through the stream and assess the situation:
			// counting the number of tokens/positions and marking if we have
			// any synonyms.

			int numTokens = 0;
			int positionCount = 0;
			boolean hasSynonyms = false;

			stream.reset();
			while (stream.incrementToken()) {
				numTokens++;
				int positionIncrement = posIncAtt.getPositionIncrement();
				if (positionIncrement != 0) {
					positionCount += positionIncrement;
				} else {
					hasSynonyms = true;
				}
			}

			// phase 2: based on token count, presence of synonyms, and options
			// formulate a single term, boolean, or phrase.

			if (numTokens == 0) {
				return null;
			} else if (numTokens == 1) {
				// single term
				return analyzeTerm(field, stream);
			} else if (quoted && positionCount > 1) {
				// phrase
				if (hasSynonyms) {
					// complex phrase with synonyms
					return analyzeMultiPhrase(field, stream, phraseSlop);
				} else {
					// simple phrase
					return analyzePhrase(field, stream, phraseSlop);
				}
			} else {
				// boolean
				if (positionCount == 1) {
					// only one position, with synonyms
					return analyzeBoolean(field, stream);
				} else {
					// complex case: multiple positions
					return analyzeMultiBoolean(field, stream, operator);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error analyzing query text", e);
		}
	}

	/**
	 * Creates simple term query from the cached tokenstream contents
	 */
	private Query analyzeTerm(String field, TokenStream stream) throws IOException {
		TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

		stream.reset();
		if (!stream.incrementToken()) {
			throw new AssertionError();
		}

		return newTermQuery(new Term(field, termAtt.getBytesRef()));
	}

	/**
	 * Creates simple boolean query from the cached tokenstream contents
	 */
	private Query analyzeBoolean(String field, TokenStream stream) throws IOException {
		TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

		stream.reset();
		List<Term> terms = new ArrayList<>();
		while (stream.incrementToken()) {
			terms.add(new Term(field, termAtt.getBytesRef()));
		}

		return newSynonymQuery(terms.toArray(new Term[terms.size()]));
	}

	private void add(BooleanQuery.Builder q, List<Term> current, BooleanClause.Occur operator) {
		if (current.isEmpty()) {
			return;
		}
		if (current.size() == 1) {
			q.add(newTermQuery(current.get(0)), operator);
		} else {
			q.add(newSynonymQuery(current.toArray(new Term[current.size()])), operator);
		}
	}

	/**
	 * Creates complex boolean query from the cached tokenstream contents
	 */
	private Query analyzeMultiBoolean(String field, TokenStream stream, BooleanClause.Occur operator)
			throws IOException {
		BooleanQuery.Builder q = newBooleanQuery();
		List<Term> currentQuery = new ArrayList<>();

		TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
		PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);

		stream.reset();
		while (stream.incrementToken()) {
			if (posIncrAtt.getPositionIncrement() != 0) {
				add(q, currentQuery, operator);
				currentQuery.clear();
			}
			currentQuery.add(new Term(field, termAtt.getBytesRef()));
		}
		add(q, currentQuery, operator);

		return q.build();
	}

	/**
	 * Creates simple phrase query from the cached tokenstream contents
	 */
	private Query analyzePhrase(String field, TokenStream stream, int slop) throws IOException {
		PhraseQuery.Builder builder = new PhraseQuery.Builder();
		builder.setSlop(slop);

		TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
		PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
		int position = -1;

		stream.reset();
		while (stream.incrementToken()) {
			if (enablePositionIncrements) {
				position += posIncrAtt.getPositionIncrement();
			} else {
				position += 1;
			}
			builder.add(new Term(field, termAtt.getBytesRef()), position);
		}

		return builder.build();
	}

	/**
	 * Creates complex phrase query from the cached tokenstream contents
	 */
	private Query analyzeMultiPhrase(String field, TokenStream stream, int slop) throws IOException {
		MultiPhraseQuery.Builder mpqb = newMultiPhraseQueryBuilder();
		mpqb.setSlop(slop);

		TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

		PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
		int position = -1;

		List<Term> multiTerms = new ArrayList<>();
		stream.reset();
		while (stream.incrementToken()) {
			int positionIncrement = posIncrAtt.getPositionIncrement();

			if (positionIncrement > 0 && multiTerms.size() > 0) {
				if (enablePositionIncrements) {
					mpqb.add(multiTerms.toArray(new Term[0]), position);
				} else {
					mpqb.add(multiTerms.toArray(new Term[0]));
				}
				multiTerms.clear();
			}
			position += positionIncrement;
			multiTerms.add(new Term(field, termAtt.getBytesRef()));
		}

		if (enablePositionIncrements) {
			mpqb.add(multiTerms.toArray(new Term[0]), position);
		} else {
			mpqb.add(multiTerms.toArray(new Term[0]));
		}
		return mpqb.build();
	}

	/**
	 * Builds a new BooleanQuery instance.
	 * <p>
	 * This is intended for subclasses that wish to customize the generated
	 * queries.
	 * 
	 * @return new BooleanQuery instance
	 */
	protected BooleanQuery.Builder newBooleanQuery() {
		return new BooleanQuery.Builder();
	}

	/**
	 * Builds a new SynonymQuery instance.
	 * <p>
	 * This is intended for subclasses that wish to customize the generated
	 * queries.
	 * 
	 * @return new Query instance
	 */
	protected Query newSynonymQuery(Term terms[]) {
		return new SynonymQuery(terms);
	}

	/**
	 * Builds a new TermQuery instance.
	 * <p>
	 * This is intended for subclasses that wish to customize the generated
	 * queries.
	 * 
	 * @param term
	 *            term
	 * @return new TermQuery instance
	 */
	protected Query newTermQuery(Term term) {
		return new TermQuery(term);
	}

	/**
	 * Builds a new MultiPhraseQuery instance.
	 * <p>
	 * This is intended for subclasses that wish to customize the generated
	 * queries.
	 * 
	 * @return new MultiPhraseQuery instance
	 */
	protected MultiPhraseQuery.Builder newMultiPhraseQueryBuilder() {
		return new MultiPhraseQuery.Builder();
	}

	public String toString(Query query, String field) {
		StringBuilder buffer = new StringBuilder();

		if (query instanceof TermQuery) {
			buffer.append(toString((TermQuery) query, field));
		} else if (query instanceof BooleanQuery) {
			buffer.append(toString((BooleanQuery) query, field));
		} else if (query instanceof WildcardQuery) {
			buffer.append(toString((WildcardQuery) query, field));
		} else if (query instanceof PhraseQuery) {
			buffer.append(toString((PhraseQuery) query, field));
		} else if (query instanceof PrefixQuery) {
			buffer.append(toString((PrefixQuery) query, field));
		} else if (query instanceof MultiPhraseQuery) {
			buffer.append(toString((MultiPhraseQuery) query, field));
		} else if (query instanceof FuzzyQuery) {
			buffer.append(toString((FuzzyQuery) query, field));
		} else if (query instanceof TermRangeQuery) {
			buffer.append(toString((TermRangeQuery) query, field));
		} else if (query instanceof ConstantScoreQuery) {
			buffer.append(toString((ConstantScoreQuery) query, field));
		} else if (query instanceof MatchAllDocsQuery) {
			buffer.append(toString((MatchAllDocsQuery) query, field));
		} else {
			buffer.append(query.toString(field));
		}

		return buffer.toString();
	}

	protected String toString(TermQuery termQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		Term term = termQuery.getTerm();

		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}
		buffer.append(term.text());
		return buffer.toString();
	}

	protected String toString(BooleanQuery booleanQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		List<BooleanClause> clauses = booleanQuery.clauses();
		boolean needParens = booleanQuery.getMinimumNumberShouldMatch() > 0;

		if (needParens) {
			buffer.append("(");
		}

		int i = 0;
		for (BooleanClause c : booleanQuery) {
			buffer.append(c.getOccur().toString());

			Query subQuery = c.getQuery();

			if (subQuery instanceof BooleanQuery) { // wrap sub-bools in parens
				buffer.append("(");
				buffer.append(toString((BooleanQuery) subQuery, field));
				buffer.append(")");
			} else {
				buffer.append(toString(subQuery, field));
			}

			if (i != clauses.size() - 1) {
				buffer.append(" ");
			}
			i += 1;
		}

		if (needParens) {
			buffer.append(")");
		}

		if (booleanQuery.getMinimumNumberShouldMatch() > 0) {
			buffer.append('~');
			buffer.append(booleanQuery.getMinimumNumberShouldMatch());
		}

		return buffer.toString();
	}

	protected String toString(WildcardQuery wildcardQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		Term term = wildcardQuery.getTerm();

		if (!wildcardQuery.getField().equals(field)) {
			buffer.append(wildcardQuery.getField());
			buffer.append(":");
		}

		buffer.append(term.text());

		return buffer.toString();
	}

	protected String toString(PhraseQuery phraseQuery, String f) {
		StringBuilder buffer = new StringBuilder();
		Term[] terms = phraseQuery.getTerms();
		String field = terms.length == 0 ? null : terms[0].field();
		int[] positions = phraseQuery.getPositions();
		int slop = phraseQuery.getSlop();

		if (field != null && !field.equals(f)) {
			buffer.append(field);
			buffer.append(":");
		}

		buffer.append("\"");
		final int maxPosition;
		if (positions.length == 0) {
			maxPosition = -1;
		} else {
			maxPosition = positions[positions.length - 1];
		}
		String[] pieces = new String[maxPosition + 1];
		for (int i = 0; i < terms.length; i++) {
			int pos = positions[i];
			String s = pieces[pos];
			if (s == null) {
				s = (terms[i]).text();
			} else {
				s = s + "|" + (terms[i]).text();
			}
			pieces[pos] = s;
		}
		for (int i = 0; i < pieces.length; i++) {
			if (i > 0) {
				buffer.append(' ');
			}
			String s = pieces[i];
			if (s == null) {
				buffer.append('?');
			} else {
				buffer.append(s);
			}
		}
		buffer.append("\"");

		if (slop != 0) {
			buffer.append("~");
			buffer.append(slop);
		}

		return buffer.toString();
	}

	protected String toString(PrefixQuery prefixQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		Term term = prefixQuery.getPrefix();

		if (!prefixQuery.getField().equals(field)) {
			buffer.append(prefixQuery.getField());
			buffer.append(':');
		}

		buffer.append(term.text());
		buffer.append('*');

		return buffer.toString();
	}

	protected String toString(MultiPhraseQuery multiPhraseQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		Term[][] termArrays = multiPhraseQuery.getTermArrays();
		int[] positions = multiPhraseQuery.getPositions();
		int slop = multiPhraseQuery.getSlop();

		buffer.append(field);
		buffer.append(":");
		buffer.append("\"");

		int lastPos = -1;

		for (int i = 0; i < termArrays.length; ++i) {
			Term[] terms = termArrays[i];
			int position = positions[i];
			if (i != 0) {
				buffer.append(" ");
				for (int j = 1; j < (position - lastPos); j++) {
					buffer.append("? ");
				}
			}
			if (terms.length > 1) {
				buffer.append("(");
				for (int j = 0; j < terms.length; j++) {
					buffer.append(terms[j].text());
					if (j < terms.length - 1)
						buffer.append(" ");
				}
				buffer.append(")");
			} else {
				buffer.append(terms[0].text());
			}
			lastPos = position;
		}
		buffer.append("\"");

		if (slop != 0) {
			buffer.append("~");
			buffer.append(slop);
		}

		return buffer.toString();
	}

	protected String toString(FuzzyQuery fuzzyQuery, String field) {
		final StringBuilder buffer = new StringBuilder();
		Term term = fuzzyQuery.getTerm();
		int maxEdits = fuzzyQuery.getMaxEdits();

		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}
		buffer.append(term.text());
		buffer.append('~');
		buffer.append(Integer.toString(maxEdits));

		return buffer.toString();
	}

	protected String toString(RegexpQuery regexpQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		Term term = regexpQuery.getRegexp();

		if (!term.field().equals(field)) {
			buffer.append(term.field());
			buffer.append(":");
		}

		buffer.append('/');
		buffer.append(term.text());
		buffer.append('/');

		return buffer.toString();
	}

	protected String toString(TermRangeQuery termRangeQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		boolean includeLower = termRangeQuery.includesLower();
		BytesRef lowerTerm = termRangeQuery.getLowerTerm();
		BytesRef upperTerm = termRangeQuery.getUpperTerm();
		boolean includeUpper = termRangeQuery.includesUpper();

		if (!termRangeQuery.getField().equals(field)) {
			buffer.append(termRangeQuery.getField());
			buffer.append(":");
		}

		buffer.append(includeLower ? '[' : '{');
		// TODO: all these toStrings for queries should just output the bytes,
		// it might not be UTF-8!
		buffer.append(
				lowerTerm != null ? ("*".equals(Term.toString(lowerTerm)) ? "\\*" : Term.toString(lowerTerm)) : "*");
		buffer.append(" TO ");
		buffer.append(
				upperTerm != null ? ("*".equals(Term.toString(upperTerm)) ? "\\*" : Term.toString(upperTerm)) : "*");
		buffer.append(includeUpper ? ']' : '}');

		return buffer.toString();
	}

	protected String toString(ConstantScoreQuery constantScoreQuery, String field) {
		Query query = constantScoreQuery.getQuery();

		return new StringBuilder("(").append(toString(query, field)).append(')').toString();
	}

	protected String toString(DisjunctionMaxQuery disjunctionMaxQuery, String field) {
		StringBuilder buffer = new StringBuilder();
		List<Query> disjuncts = disjunctionMaxQuery.getDisjuncts();
		float tieBreakerMultiplier = disjunctionMaxQuery.getTieBreakerMultiplier();

		buffer.append("(");

		for (int i = 0; i < disjuncts.size(); i++) {
			Query subquery = disjuncts.get(i);
			if (subquery instanceof BooleanQuery) { // wrap sub-bools in parens
				buffer.append("(");
				buffer.append(toString(subquery, field));
				buffer.append(")");
			} else
				buffer.append(toString(subquery, field));
			if (i != disjuncts.size() - 1)
				buffer.append(" | ");
		}

		buffer.append(")");
		if (tieBreakerMultiplier != 0.0f) {
			buffer.append("~");
			buffer.append(tieBreakerMultiplier);
		}

		return buffer.toString();
	}

	protected String toString(MatchAllDocsQuery matchAllDocsQuery) {
		return "*:*";
	}

	protected String toString(SynonymQuery synonymQuery, String field) {
		StringBuilder builder = new StringBuilder("(");
		List<Term> terms = synonymQuery.getTerms();

		for (int i = 0; i < terms.size(); i++) {
			if (i != 0) {
				builder.append(" ");
			}

			TermQuery termQuery = new TermQuery(terms.get(i));

			builder.append(toString(termQuery, field));
		}
		builder.append(")");
		return builder.toString();
	}
}

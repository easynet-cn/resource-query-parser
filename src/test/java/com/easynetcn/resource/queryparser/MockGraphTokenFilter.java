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
package com.easynetcn.resource.queryparser;

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import com.carrotsearch.randomizedtesting.generators.RandomNumbers;

// TODO: sometimes remove tokens too...?

/**
 * Randomly inserts overlapped (posInc=0) tokens with posLength sometimes &gt;
 * 1. The chain must have an OffsetAttribute.
 */

public final class MockGraphTokenFilter extends LookaheadTokenFilter<LookaheadTokenFilter.Position> {

	private static boolean DEBUG = false;

	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

	private final long seed;
	private Random random;

	public MockGraphTokenFilter(Random random, TokenStream input) {
		super(input);
		seed = random.nextLong();
	}

	@Override
	protected Position newPosition() {
		return new Position();
	}

	@Override
	protected void afterPosition() throws IOException {
		if (DEBUG) {
			System.out.println("MockGraphTF.afterPos");
		}
		if (random.nextInt(7) == 5) {

			final int posLength = nextInt(random, 1, 5);

			if (DEBUG) {
				System.out.println("  do insert! posLen=" + posLength);
			}

			final Position posEndData = positions.get(outputPos + posLength);

			// Look ahead as needed until we figure out the right
			// endOffset:
			while (!end && posEndData.endOffset == -1 && inputPos <= (outputPos + posLength)) {
				if (!peekToken()) {
					break;
				}
			}

			if (posEndData.endOffset != -1) {
				// Notify super class that we are injecting a token:
				insertToken();
				clearAttributes();
				posLenAtt.setPositionLength(posLength);
				termAtt.append(randomUnicodeString(random));
				posIncAtt.setPositionIncrement(0);
				offsetAtt.setOffset(positions.get(outputPos).startOffset, posEndData.endOffset);
				if (DEBUG) {
					System.out.println("  inject: outputPos=" + outputPos + " startOffset=" + offsetAtt.startOffset()
							+ " endOffset=" + offsetAtt.endOffset() + " posLength=" + posLenAtt.getPositionLength());
				}
				// TODO: set TypeAtt too?
			} else {
				// Either 1) the tokens ended before our posLength,
				// or 2) our posLength ended inside a hole from the
				// input. In each case we just skip the inserted
				// token.
			}
		}
	}

	@Override
	public void reset() throws IOException {
		super.reset();
		// NOTE: must be "deterministically random" because
		// BaseTokenStreamTestCase pulls tokens twice on the
		// same input and asserts they are the same:
		this.random = new Random(seed);
	}

	@Override
	public void close() throws IOException {
		super.close();
		this.random = null;
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (DEBUG) {
			System.out.println("MockGraphTF.incr inputPos=" + inputPos + " outputPos=" + outputPos);
		}
		if (random == null) {
			throw new IllegalStateException("incrementToken called in wrong state!");
		}
		return nextToken();
	}

	public static int nextInt(Random r, int start, int end) {
		return RandomNumbers.randomIntBetween(r, start, end);
	}

	public static String randomUnicodeString(Random r) {
		return randomUnicodeString(r, 20);
	}

	public static String randomUnicodeString(Random r, int maxLength) {
		final int end = nextInt(r, 0, maxLength);
		if (end == 0) {
			// allow 0 length
			return "";
		}
		final char[] buffer = new char[end];
		randomFixedLengthUnicodeString(r, buffer, 0, buffer.length);
		return new String(buffer, 0, end);
	}

	public static void randomFixedLengthUnicodeString(Random random, char[] chars, int offset, int length) {
		int i = offset;
		final int end = offset + length;
		while (i < end) {
			final int t = random.nextInt(5);
			if (0 == t && i < length - 1) {
				// Make a surrogate pair
				// High surrogate
				chars[i++] = (char) nextInt(random, 0xd800, 0xdbff);
				// Low surrogate
				chars[i++] = (char) nextInt(random, 0xdc00, 0xdfff);
			} else if (t <= 1) {
				chars[i++] = (char) random.nextInt(0x80);
			} else if (2 == t) {
				chars[i++] = (char) nextInt(random, 0x80, 0x7ff);
			} else if (3 == t) {
				chars[i++] = (char) nextInt(random, 0x800, 0xd7ff);
			} else if (4 == t) {
				chars[i++] = (char) nextInt(random, 0xe000, 0xffff);
			}
		}
	}
}

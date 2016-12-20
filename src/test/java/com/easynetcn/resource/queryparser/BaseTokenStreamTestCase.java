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

import java.util.Random;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.AttributeFactory;

import com.carrotsearch.randomizedtesting.RandomizedContext;

/**
 * Base class for all Lucene unit tests that use TokenStreams.
 * <p>
 * When writing unit tests for analysis components, it's highly recommended to
 * use the helper methods here (especially in conjunction with
 * {@link MockAnalyzer} or {@link MockTokenizer}), as they contain many
 * assertions and checks to catch bugs.
 * 
 * @see MockAnalyzer
 * @see MockTokenizer
 */
public abstract class BaseTokenStreamTestCase {
	// some helpers to test Analyzers and TokenStreams:

	/** Returns a random AttributeFactory impl */
	public static AttributeFactory newAttributeFactory(Random random) {
		switch (random.nextInt(3)) {
		case 0:
			return TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY;
		case 1:
			return org.apache.lucene.analysis.Token.TOKEN_ATTRIBUTE_FACTORY;
		case 2:
			return AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY;
		default:
			throw new AssertionError("Please fix the Random.nextInt() call above");
		}
	}

	/** Returns a random AttributeFactory impl */
	public static AttributeFactory newAttributeFactory() {
		return newAttributeFactory(random());
	}

	public static Random random() {
		return RandomizedContext.current().getRandom();
	}
}

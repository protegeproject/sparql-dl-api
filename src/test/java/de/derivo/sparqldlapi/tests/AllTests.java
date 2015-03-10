// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	QueryArgumentTest.class,
	QueryAtomTest.class,
	QueryTest.class, 
	QueryTokenTest.class,
	QueryTokenizerTest.class,
	QueryParserTest.class,
	QueryBindingTest.class,
	QueryResultTest.class,
	QueryEngineStrictModeTest.class
})
public class AllTests 
{}

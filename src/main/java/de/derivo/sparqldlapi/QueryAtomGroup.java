// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.List;

/**
 * The QueryAtomGroup class contains a group of atoms of a WHERE {} or UNION {} statement.
 * 
 * @author Mario Volke
 */
public interface QueryAtomGroup 
{
	/**
	 * Check whether there are any atoms in the group.
	 * @return True if there are no atoms at all.
	 */
	boolean isEmpty();
	
	/**
	 * Get an unodifiable list of all query atoms in the group.
	 */
	List<QueryAtom> getAtoms();
	
	/**
	 * Print the group as string.*
	 * @return a string
	 */
	String toString();
	
	@Override
	boolean equals(Object obj);
	
	@Override
	int hashCode();
}

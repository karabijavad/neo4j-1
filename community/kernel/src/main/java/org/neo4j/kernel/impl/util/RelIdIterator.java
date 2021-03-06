/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.util;

import org.neo4j.kernel.impl.util.RelIdArray.DirectionWrapper;

public interface RelIdIterator
{
    int getType();

    RelIdIterator updateSource( RelIdArray newSource, DirectionWrapper direction );

    // TODO remove
    boolean hasNext();

    /**
     * Tells this iterator to try another round with all its directions
     * starting from each their previous states. Called from IntArrayIterator,
     * when it finds out it has gotten more relationships of this type.
     */
    void doAnotherRound();

    long next();
}
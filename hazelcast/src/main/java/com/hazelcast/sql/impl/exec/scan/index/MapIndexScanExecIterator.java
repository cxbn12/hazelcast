/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.sql.impl.exec.scan.index;

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.map.impl.MapContainer;
import com.hazelcast.query.impl.InternalIndex;
import com.hazelcast.query.impl.QueryableEntry;
import com.hazelcast.sql.impl.QueryException;
import com.hazelcast.sql.impl.exec.scan.KeyValueIterator;
import com.hazelcast.sql.impl.expression.ExpressionEvalContext;
import com.hazelcast.sql.impl.schema.map.MapTableUtils;
import com.hazelcast.sql.impl.type.QueryDataType;

import java.util.Iterator;
import java.util.List;

/**
 * Iterator for index-based partitioned map access.
 */
@SuppressWarnings("rawtypes")
public class MapIndexScanExecIterator implements KeyValueIterator {

    private final MapContainer map;
    private final String indexName;
    private final int componentCount;
    private final IndexFilter indexFilter;
    private final List<QueryDataType> expectedConverterTypes;
    private final ExpressionEvalContext evalContext;

    private final Iterator<QueryableEntry> iterator;

    private Data currentKey;
    private Object currentValue;
    private Data nextKey;
    private Object nextValue;

    public MapIndexScanExecIterator(
        MapContainer map,
        String indexName,
        int componentCount,
        IndexFilter indexFilter,
        List<QueryDataType> expectedConverterTypes,
        ExpressionEvalContext evalContext
    ) {
        this.map = map;
        this.indexName = indexName;
        this.componentCount = componentCount;
        this.indexFilter = indexFilter;
        this.expectedConverterTypes = expectedConverterTypes;
        this.evalContext = evalContext;

        iterator = getIndexEntries();

        advance0();
    }

    @Override
    public boolean tryAdvance() {
        if (!done()) {
            currentKey = nextKey;
            currentValue = nextValue;

            advance0();

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean done() {
        return nextKey == null;
    }

    @Override
    public Object getKey() {
        return currentKey;
    }

    @Override
    public Object getValue() {
        return currentValue;
    }

    private void advance0() {
        if (iterator.hasNext()) {
            QueryableEntry<?, ?> entry = iterator.next();

            nextKey = entry.getKeyData();
            nextValue = entry.getValue();
        } else {
            nextKey = null;
            nextValue = null;
        }
    }

    private Iterator<QueryableEntry> getIndexEntries() {
        MapContainer mapContainer = map.getMapServiceContext().getMapContainer(map.getName());
        InternalIndex index = mapContainer.getIndexes().getIndex(indexName);

        if (index == null) {
            throw QueryException.error("Index \"" + indexName + "\" doesn't exist");
        }

        if (indexFilter == null) {
            // No filter => this is a full scan (e.g. for HD)
            return index.getRecordIterator();
        }

        int actualComponentCount = index.getComponents().length;

        if (actualComponentCount != componentCount) {
            throw QueryException.error("Index \"" + indexName + "\" has " + actualComponentCount + " component(s), but "
                + componentCount + " expected");
        }

        // Validate component types
        List<QueryDataType> currentConverterTypes = MapTableUtils.indexConverterToSqlTypes(index.getConverter());

        validateConverterTypes(index, expectedConverterTypes, currentConverterTypes);

        // Query the index
        return indexFilter.getEntries(index, evalContext);
    }

    private void validateConverterTypes(
        InternalIndex index,
        List<QueryDataType> expectedConverterTypes,
        List<QueryDataType> actualConverterTypes
    ) {
        for (int i = 0; i < Math.min(expectedConverterTypes.size(), actualConverterTypes.size()); i++) {
            QueryDataType expected = expectedConverterTypes.get(i);
            QueryDataType actual = actualConverterTypes.get(i);

            if (!expected.equals(actual)) {
                String component = index.getComponents()[i];

                throw QueryException.dataException("Index \"" + indexName + "\" has component \"" + component + "\" of type "
                    + actual.getTypeFamily() + ", but " + expected.getTypeFamily() + " was expected");
            }
        }

        if (expectedConverterTypes.size() > actualConverterTypes.size()) {
            QueryDataType expected = expectedConverterTypes.get(actualConverterTypes.size());
            String component = index.getComponents()[actualConverterTypes.size()];

            throw QueryException.dataException("Index \"" + indexName + "\" do not have suitable SQL converter for component \""
                + component + "\" (expected " + expected.getTypeFamily() + ")");
        }
    }
}
package org.rhq.server.metrics;

import static java.util.Collections.EMPTY_SET;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Row;

/**
 * Iterates over rows from different statements.
 */
public class RowIterable implements Iterable<Row> {

    private final StorageSession ss;

    private final List<BoundStatement> list = new ArrayList<BoundStatement>();

    public RowIterable(StorageSession ss) {
        this.ss = ss;
    }

    public void add(BoundStatement bs) {
        list.add(bs);
    }

    @Override
    public Iterator<Row> iterator() {
        final Iterator<BoundStatement> outer = list.iterator();
        return new Iterator<Row>() {

            Iterator<Row> rows = EMPTY_SET.iterator();

            @Override
            public boolean hasNext() {
                if (!rows.hasNext() && outer.hasNext()) {
                    BoundStatement bs = outer.next();
                    rows = ss.execute(bs).iterator();
                }
                return rows.hasNext();
            }

            @Override
            public Row next() {
                if (!hasNext()) {
                    throw new AssertionError();
                }
                return rows.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

}
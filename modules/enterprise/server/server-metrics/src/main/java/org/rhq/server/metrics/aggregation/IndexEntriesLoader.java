package org.rhq.server.metrics.aggregation;

import static org.rhq.server.metrics.domain.MetricsTable.RAW;

import java.util.ArrayList;
import java.util.List;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import org.joda.time.DateTime;
import org.rhq.server.metrics.MetricsDAO;
import org.rhq.server.metrics.RowIterable;
import org.rhq.server.metrics.StorageResultSetFuture;
import org.rhq.server.metrics.domain.CacheIndexEntry;
import org.rhq.server.metrics.domain.CacheIndexEntryMapper;
import org.rhq.server.metrics.domain.MetricsTable;

/**
 * Loads {@link org.rhq.server.metrics.domain.CacheIndexEntry index entries} during aggregation.
 *
 * @author John Sanda
 */
public class IndexEntriesLoader {

    private DateTime currentTimeSlice;

    private DateTime currentDay;

    private MetricsDAO dao;

    private CacheIndexEntryMapper mapper;

    public IndexEntriesLoader(DateTime currentTimeSlice, DateTime currentDay, MetricsDAO dao) {
        this.currentTimeSlice = currentTimeSlice;
        this.currentDay = currentDay;
        this.dao = dao;
        mapper = new CacheIndexEntryMapper();
    }

    /**
     * We store a configurable amount of past data where the amount is specified as a duration in days. Suppose that the
     * duration is set at 4 days, and the current time is 14:00 Friday. This method will query the index as far back
     * as 14:00 on Monday, and each day up to the current time slice of today will be queried for past data.
     *
     * @return The past cache index entries
     */
    public List<CacheIndexEntry> loadPastIndexEntries(DateTime startDay) {
        try {
            DateTime day = startDay;
            DateTime timeSlice = day.plusHours(currentTimeSlice.getHourOfDay());
            List<CacheIndexEntry> indexEntries = new ArrayList<CacheIndexEntry>();
            RowIterable rows = dao.findPastCacheIndexEntriesBeforeToday(RAW, day.getMillis(),
                timeSlice.getMillis());

            addResultSet(indexEntries, rows);
            day = day.plusDays(1);

            RowIterable future;
            while (day.isBefore(currentDay)) {
                future = dao.findCacheIndexEntriesByDay(RAW, day.getMillis());
                addResultSet(indexEntries, future);
                day = day.plusDays(1);
            }

            future = dao.findPastCacheIndexEntriesFromToday(RAW, currentDay.getMillis(),
                currentTimeSlice.getMillis());
            addResultSet(indexEntries, future);

            return indexEntries;
        } catch (Exception e) {
            throw new CacheIndexQueryException("Failed to load cache index entries prior to current time slice " +
                currentTimeSlice, e);
        }
    }

    /**
     * Returns cache index entries for the current time slice.
     *
     * @param pageSize The limit of rows to return per query
     * @param table One of raw, 1 hr, or 6hr
     * @return The cache index entries for the current time slice
     */
    public List<CacheIndexEntry> loadCurrentCacheIndexEntries(int pageSize, MetricsTable table) {
        try {
            List<CacheIndexEntry> indexEntries = new ArrayList<CacheIndexEntry>();
            RowIterable rows = dao.findCurrentCacheIndexEntries(table, currentDay.getMillis(),
                currentTimeSlice.getMillis());

            addResultSet(indexEntries, rows);
            if (indexEntries.isEmpty())
                return indexEntries;

            while (indexEntries.size() % pageSize == 0) {
                int startScheduleId = indexEntries.get(indexEntries.size() - 1).getStartScheduleId();
                rows = dao.findCurrentCacheIndexEntries(table, currentDay.getMillis(),
                    currentTimeSlice.getMillis(), startScheduleId);
                int before = indexEntries.size();
                addResultSet(indexEntries, rows);
                if (before == indexEntries.size())
                    break;
            }

            return indexEntries;
        } catch (Exception e) {
            throw new CacheIndexQueryException("Failed to load cache index entries for current time slice " +
                currentTimeSlice, e);
        }
    }

    private void addResultSet(List<CacheIndexEntry> indexEntries, Iterable<Row> rows) {
        for (Row row : rows) {
            indexEntries.add(mapper.map(row));
        }
    }

    private void addResultSet(List<CacheIndexEntry> indexEntries, StorageResultSetFuture future) {
        addResultSet(indexEntries, future.get());
    }

    private void addResultSet(List<CacheIndexEntry> indexEntries, ResultSet resultSet) {
        for (Row row : resultSet) {
            indexEntries.add(mapper.map(row));
        }
    }

}

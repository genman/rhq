/*
 *
 *  * RHQ Management Platform
 *  * Copyright (C) 2005-2012 Red Hat, Inc.
 *  * All rights reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License, version 2, as
 *  * published by the Free Software Foundation, and/or the GNU Lesser
 *  * General Public License, version 2.1, also as published by the Free
 *  * Software Foundation.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License and the GNU Lesser General Public License
 *  * for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * and the GNU Lesser General Public License along with this program;
 *  * if not, write to the Free Software Foundation, Inc.,
 *  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package org.rhq.server.metrics;

import org.joda.time.Days;
import org.joda.time.Duration;
import org.joda.time.ReadablePeriod;

/**
 * @author John Sanda
 */
public class MetricsConfiguration {

    private ReadablePeriod rawRetention = Days.days(7);

    private ReadablePeriod oneHourRetention = Days.days(14);

    private ReadablePeriod sixHourRetention = Days.days(31);

    private ReadablePeriod twentyFourHourRetention = Days.days(365);

    private int rawTTL = Duration.standardDays(7).toStandardSeconds().getSeconds();

    private int oneHourTTL = Duration.standardDays(14).toStandardSeconds().getSeconds();

    private int sixHourTTL = Duration.standardDays(31).toStandardSeconds().getSeconds();

    private int twentyFourHourTTL = Duration.standardDays(365).toStandardSeconds().getSeconds();

    private Duration rawTimeSliceDuration = Duration.standardHours(1);

    private Duration oneHourTimeSliceDuration = Duration.standardHours(6);

    private Duration sixHourTimeSliceDuration = Duration.standardHours(24);

    private int indexPageSize = Integer.parseInt(System.getProperty("rhq.metrics.index.page-size", "2500"));

    // TODO make this configurable
    private int indexPartitions = 10;

    public int getRawTTL() {
        return rawTTL;
    }

    public MetricsConfiguration setRawTTL(int rawTTL) {
        this.rawTTL = rawTTL;
        return this;
    }

    public int getOneHourTTL() {
        return oneHourTTL;
    }

    public MetricsConfiguration setOneHourTTL(int oneHourTTL) {
        this.oneHourTTL = oneHourTTL;
        return this;
    }

    public int getSixHourTTL() {
        return sixHourTTL;
    }

    public MetricsConfiguration setSixHourTTL(int sixHourTTL) {
        this.sixHourTTL = sixHourTTL;
        return this;
    }

    public int getTwentyFourHourTTL() {
        return twentyFourHourTTL;
    }

    public MetricsConfiguration setTwentyFourHourTTL(int twentyFourHourTTL) {
        this.twentyFourHourTTL = twentyFourHourTTL;
        return this;
    }

    public ReadablePeriod getRawRetention() {
        return rawRetention;
    }

    public MetricsConfiguration setRawRetention(Duration retention) {
        rawRetention = rawRetention;
        return this;
    }

    public ReadablePeriod getOneHourRetention() {
        return oneHourRetention;
    }

    public MetricsConfiguration setOneHourRetention(ReadablePeriod retention) {
        oneHourRetention = retention;
        return this;
    }

    public ReadablePeriod getSixHourRetention() {
        return sixHourRetention;
    }

    public MetricsConfiguration setSixHourRetention(ReadablePeriod retention) {
        sixHourRetention = retention;
        return this;
    }

    public ReadablePeriod getTwentyFourHourRetention() {
        return twentyFourHourRetention;
    }

    public MetricsConfiguration setTwentyFourHourRetention(ReadablePeriod retention) {
        twentyFourHourRetention = retention;
        return this;
    }

    public Duration getRawTimeSliceDuration() {
        return rawTimeSliceDuration;
    }

    public void setRawTimeSliceDuration(Duration rawTimeSliceDuration) {
        this.rawTimeSliceDuration = rawTimeSliceDuration;
    }

    public Duration getOneHourTimeSliceDuration() {
        return oneHourTimeSliceDuration;
    }

    public MetricsConfiguration setOneHourTimeSliceDuration(Duration oneHourTimeSliceDuration) {
        this.oneHourTimeSliceDuration = oneHourTimeSliceDuration;
        return this;
    }

    public Duration getSixHourTimeSliceDuration() {
        return sixHourTimeSliceDuration;
    }

    public MetricsConfiguration setSixHourTimeSliceDuration(Duration sixHourTimeSliceDuration) {
        this.sixHourTimeSliceDuration = sixHourTimeSliceDuration;
        return this;
    }

    public int getIndexPageSize() {
        return indexPageSize;
    }

    public MetricsConfiguration setIndexPageSize(int indexPageSize) {
        this.indexPageSize = indexPageSize;
        return this;
    }

    public int getIndexPartitions() {
        return indexPartitions;
    }

    public MetricsConfiguration setIndexPartitions(int indexPartitions) {
        this.indexPartitions = indexPartitions;
        return this;
    }
}

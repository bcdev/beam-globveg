/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.globveg;

import com.bc.ceres.binding.PropertySet;
import org.esa.beam.binning.AbstractAggregator;
import org.esa.beam.binning.Aggregator;
import org.esa.beam.binning.AggregatorConfig;
import org.esa.beam.binning.AggregatorDescriptor;
import org.esa.beam.binning.BinContext;
import org.esa.beam.binning.Observation;
import org.esa.beam.binning.VariableContext;
import org.esa.beam.binning.Vector;
import org.esa.beam.binning.WritableVector;
import org.esa.beam.binning.support.GrowableVector;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.util.math.MathUtils;

import java.util.Arrays;

/**
 * An aggregator that selects the value closest to the mean in temporal aggregation,
 * no spatial aggregation.
 * This aggregator does not implement the two cycles foreseen by Pinty, with the first filtering outliers
 * and the second selecting the value. Frequently there are only a few observations,
 */
public class AggregatorPinty extends AbstractAggregator {

    private final int varIndex;
    private final int maskIndex;
    private final String mlName;
    private final String tlName;

    public AggregatorPinty(VariableContext varCtx, String varName, String maskName) {
        super(Descriptor.NAME, createSpatialFeatures(varName), createFeatures(varName), createFeatures(varName));
        if (varCtx == null) {
            throw new NullPointerException("varCtx");
        }
        varIndex = varCtx.getVariableIndex(varName);
        if (varIndex < 0) {
            throw new IllegalArgumentException("varIndex < 0");
        }
        maskIndex = varCtx.getVariableIndex(maskName);
        mlName = "ml." + varName;
        tlName = "tl." + varName;
    }

    private static String[] createSpatialFeatures(String varName) {
        return new String[]{
                varName,
                varName + "_mjd"
        };
    }

    private static String[] createFeatures(String varName) {
        return new String[]{
                varName,
                varName + "_mjd",
                varName + "_count",
                varName + "_sigma"
        };
    }

    @Override
    public void initSpatial(BinContext ctx, WritableVector spatialVector) {
        spatialVector.set(0, Float.NaN);
        spatialVector.set(1, Float.NaN);
    }

    @Override
    public void aggregateSpatial(BinContext ctx, Observation observationVector, WritableVector spatialVector) {
        // we assume there is only one observation per bin cell. Else, 'Pinty' is not applicable.
        final float value = observationVector.get(varIndex);
        boolean isValid = maskIndex < 0 || observationVector.get(maskIndex) == 1.0f;
        if (isValid && !Float.isNaN(value)) {
            final float time = (float) observationVector.getMJD();
            spatialVector.set(0, value);
            spatialVector.set(1, time);
        }
    }

    @Override
    public void completeSpatial(BinContext ctx, int numSpatialObs, WritableVector spatialVector) {
    }

    @Override
    public void initTemporal(BinContext ctx, WritableVector temporalVector) {
        ctx.put(mlName, new GrowableVector(256));
        ctx.put(tlName, new GrowableVector(256));
    }


    @Override
    public void aggregateTemporal(BinContext ctx, Vector spatialVector, int numSpatialObs, WritableVector temporalVector) {
        // check necessary because we cannot suppress NaN values in spatial binning
        if (!Float.isNaN(spatialVector.get(0))) {
            GrowableVector measurementsVec = ctx.get(mlName);
            GrowableVector timeVec = ctx.get(tlName);
            measurementsVec.add(spatialVector.get(0));
            timeVec.add(spatialVector.get(1));
        }
    }

    @Override
    public void completeTemporal(BinContext ctx, int numTemporalObs, WritableVector temporalVector) {
        GrowableVector measurementsVec = ctx.get(mlName);
        GrowableVector timeVec = ctx.get(tlName);
        float[] measurements = measurementsVec.getElements();
        float[] times = timeVec.getElements();

        if (measurements.length == 0) {
            temporalVector.set(0, Float.NaN);
            temporalVector.set(1, Float.NaN);
            temporalVector.set(2, 0.0f);
            temporalVector.set(3, Float.NaN);
        } else {
            double sum = 0.0f;
            double sumSqr = 0.0f;
            for (float measurement : measurements) {
                sum += measurement;
                sumSqr += measurement * measurement;
            }
            final float mean = (float) (sum / measurements.length);
            final float sigmaSqr = (float) (sumSqr / measurements.length - mean * mean);
            final float sigma = sigmaSqr > 0.0f ? (float) Math.sqrt(sigmaSqr) : 0.0f;

            float bestMeasurement = measurements[0];
            float bestTime = times[0];
            for (int i = 1; i < measurements.length; ++i) {
                final float currentDistance = Math.abs(measurements[i] - mean);
                final float bestDistance = Math.abs(bestMeasurement - mean);
                if (currentDistance < (bestDistance - 1E-6f) ||
                        (MathUtils.equalValues(currentDistance, bestDistance, 1E-6f) && measurements[i] > bestMeasurement) || // same distance, but larger value
                        (measurements[i] == bestMeasurement && times[i] < bestTime)) // same value, but earlier
                {
                    bestMeasurement = measurements[i];
                    bestTime = times[i];
                }
            }

            temporalVector.set(0, bestMeasurement);
            temporalVector.set(1, bestTime);
            temporalVector.set(2, measurements.length);
            temporalVector.set(3, sigma);
        }
    }

    @Override
    public void computeOutput(Vector temporalVector, WritableVector outputVector) {
        for (int i = 0; i < 4; ++i) {
            outputVector.set(i, temporalVector.get(i));
        }
    }

    @Override
    public String toString() {
        return "AggregatorPinty{" +
                "varIndex=" + varIndex +
                ", maskIndex=" + maskIndex +
                ", spatialFeatureNames=" + Arrays.toString(getSpatialFeatureNames()) +
                ", temporalFeatureNames=" + Arrays.toString(getTemporalFeatureNames()) +
                ", outputFeatureNames=" + Arrays.toString(getOutputFeatureNames()) +
                '}';
    }

    public static class Config extends AggregatorConfig {
        @Parameter
        String varName;
        @Parameter
        String maskName;

        public Config() {
            super(Descriptor.NAME);
        }

        @Override
        public String[] getVarNames() {
            return new String[]{varName, maskName};
        }
    }

    public static class Descriptor implements AggregatorDescriptor {

        public static final String NAME = "PINTY";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public AggregatorConfig createAggregatorConfig() {
            return new Config();
        }

        @Override
        public Aggregator createAggregator(VariableContext varCtx, AggregatorConfig aggregatorConfig) {
            PropertySet propertySet = aggregatorConfig.asPropertySet();
            return new AggregatorPinty(varCtx,
                                       (String) propertySet.getValue("varName"),
                                       (String) propertySet.getValue("maskName"));
        }
    }
}

package org.esa.beam.globveg;

import org.esa.beam.binning.BinContext;
import org.esa.beam.binning.support.VectorImpl;
import org.junit.Before;
import org.junit.Test;

import static java.lang.Float.NaN;
import static org.esa.beam.globveg.AggregatorTestUtils.*;
import static org.junit.Assert.assertEquals;

/**
 * TODO add API doc
 *
 * @author Martin Boettcher
 */
public class AggregatorPintyTest {

    BinContext ctx;

    @Before
    public void setUp() throws Exception {
        ctx = createCtx();
    }

    @Test
    public void testMetadata() {
        AggregatorPinty agg = new AggregatorPinty(new MyVariableContext("a", "va", "b", "vb"), "a", "va", null);

        assertEquals("PINTY", agg.getName());

        assertEquals(2, agg.getSpatialFeatureNames().length);
        assertEquals("a", agg.getSpatialFeatureNames()[0]);
        assertEquals("a_mjd", agg.getSpatialFeatureNames()[1]);

        assertEquals(4, agg.getTemporalFeatureNames().length);
        assertEquals("a", agg.getTemporalFeatureNames()[0]);
        assertEquals("a_mjd", agg.getTemporalFeatureNames()[1]);
        assertEquals("a_count", agg.getTemporalFeatureNames()[2]);
        assertEquals("a_sigma", agg.getTemporalFeatureNames()[3]);

        assertEquals(4, agg.getOutputFeatureNames().length);
        assertEquals("a", agg.getOutputFeatureNames()[0]);
        assertEquals("a_mjd", agg.getOutputFeatureNames()[1]);
        assertEquals("a_count", agg.getOutputFeatureNames()[2]);
        assertEquals("a_sigma", agg.getOutputFeatureNames()[3]);
    }

    @Test
    public void testAggregator() {
        AggregatorPinty agg = new AggregatorPinty(new MyVariableContext("a", "va", "b", "vb"), "a", "va", null);

        VectorImpl svec = vec(NaN, NaN);
        VectorImpl tvec = vec(NaN, NaN, NaN, NaN);
        VectorImpl out = vec(NaN, NaN, NaN, NaN);

        agg.initSpatial(ctx, svec);
        assertEquals(Float.NaN, svec.get(0), 0.0f);
        assertEquals(Float.NaN, svec.get(1), 0.0f);

        agg.aggregateSpatial(ctx, obs(2013.38f, 1.5f, 1f, 2.5f, 0f), svec);
        assertEquals(1.5f, svec.get(0), 1e-5f);
        assertEquals(2013.38f, svec.get(1), 1e-5f);

        agg.completeSpatial(ctx, 1, svec);
        assertEquals(1.5f, svec.get(0), 1e-5f);
        assertEquals(2013.38f, svec.get(1), 1e-5f);

        agg.initTemporal(ctx, tvec);
//        assertEquals(Float.NaN, tvec.get(0), 0.0f);
//        assertEquals(Float.NaN, tvec.get(1), 0.0f);
//        assertEquals(Float.NaN, tvec.get(2), 0.0f);
//        assertEquals(Float.NaN, tvec.get(3), 0.0f);

        agg.aggregateTemporal(ctx, vec(1.5f, 2013.38f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(1.6f, 2013.48f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(1.8f, 2013.58f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(Float.NaN, Float.NaN), 1, tvec);

        agg.completeTemporal(ctx, 4, tvec);

        agg.computeOutput(tvec, out);
        assertEquals(1.6f, tvec.get(0), 1e-5f);
        assertEquals(2013.48f, tvec.get(1), 1e-5f);
        assertEquals(3f, tvec.get(2), 1e-5f);
        assertEquals(0.124721855f, tvec.get(3), 1e-5f);
    }

}

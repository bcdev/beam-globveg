package org.esa.beam.globveg;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;

public class GlobVegOpTest {
    @Before
    public void setUp() throws Exception {
        System.out.println("Starting next test");
    }

    @After
    public void after() {
        System.gc();
    }


    @Test
    public void testNdviKg() throws OperatorException, ParseException {
        final float refl_6 = 0.08f;
        final float refl_7 = 0.12f;
        final float refl_10 = 0.2f;
        final float refl_12 = 0.3f;
        final float refl_13 = 0.5f;
        final float refl_14 = 0.6f;
        final float refl_15 = 0.4f;

        float alpha = 0.5f;
        float beta = 0.1f;

        final float ndvi = GlobVegOp.computeNdviKgValue(alpha, beta, refl_6, refl_7, refl_10, refl_12, refl_13, refl_14, refl_15);
        Assert.assertEquals(0.333333f, ndvi, 1.E-5);    // (0.2 - 0.1)/(0.2 + 0.1)
    }

}

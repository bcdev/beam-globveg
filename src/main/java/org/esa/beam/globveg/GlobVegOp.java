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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.*;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.NdviOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.idepix.algorithms.globalbedo.GlobAlbedoOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.util.ProductUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * The combining operator for the GlobVeg project.
 */
@OperatorMetadata(alias = "GlobVeg",
                  authors = "Marco Zuehlke, Martin Boettcher, Olaf Danne",
                  copyright = "Brockmann Consult GmbH",
                  version = "1.0",
                  description = "combines fAPAR and LAI for GlobVeg, optionally adds Kurt Guenther NDVI")
public class GlobVegOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    @Parameter(defaultValue = "true", label = " Compute an NDVI band from Kurt Guenther algorithm")
    private boolean computeNdviKg = true;

    @Parameter(defaultValue = "false", label = " Copy reflectance bands to target product")
    private boolean outputReflectanceBands = false;

    @Parameter(defaultValue = "false", label = " Write also simple NDVI = (rad10 - rad6)/(rad10 + rad6) to target product")
    private boolean outputNdviSimple = false;

    private Band timeBand;
    private Band validFaparBand;
    private Band validLaiBand;
    private Band validFaparMask;
    private Band validLaiMask;
    private Band cloudFreeBand;
    private Band ndviKgBand;

    private Band[] merisReflBands;

    private static final float ALPLA_KG = 0.2744f;
    private static final float BETA_KG = 0.0839f;

    @Override
    public void initialize() throws OperatorException {

        final Product correctedL1b = GPF.createProduct("Meris.CorrectRadiometry", GPF.NO_PARAMS, sourceProduct);
        final Product rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS, sourceProduct);
        final Product ndviSimpleProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(NdviOp.class), GPF.NO_PARAMS, sourceProduct);
        final Product faparProduct = GPF.createProduct("Fapar", GPF.NO_PARAMS, correctedL1b);
        final Product laiProduct = GPF.createProduct("ToaVeg", GPF.NO_PARAMS, correctedL1b);

        Product targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(),
                                            sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        Band band = ProductUtils.copyBand("FAPAR", faparProduct, "fapar", targetProduct, true);
        band.setValidPixelExpression("valid_fapar == 1");
        band.setNoDataValueUsed(false);

        band = ProductUtils.copyBand("LAI", laiProduct, "lai", targetProduct, true);
        band.setValidPixelExpression("valid_lai == 1");
        band.setNoDataValueUsed(false);

        String faparExpression = faparProduct.getBand("FAPAR").getValidMaskExpression();
        BandMathsOp bandMathsOp1 = BandMathsOp.createBooleanExpressionBand(faparExpression, faparProduct);
        validFaparMask = bandMathsOp1.getTargetProduct().getBandAt(0);

        String laiExpression = laiProduct.getBand("LAI").getValidMaskExpression();
        BandMathsOp bandMathsOp2 = BandMathsOp.createBooleanExpressionBand(laiExpression, laiProduct);
        validLaiMask = bandMathsOp2.getTargetProduct().getBandAt(0);

        timeBand = targetProduct.addBand("obs_time", ProductData.TYPE_FLOAT32);
        validFaparBand = targetProduct.addBand("valid_fapar", ProductData.TYPE_INT8);
        validLaiBand = targetProduct.addBand("valid_lai", ProductData.TYPE_INT8);

        if (outputReflectanceBands) {
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                final Band b = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
                ProductUtils.copyBand(b.getName(), rad2reflProduct, targetProduct, true);
            }
        }

        if (computeNdviKg) {
            merisReflBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                merisReflBands[i] = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
            }

            ndviKgBand = targetProduct.addBand("ndvi_kg", ProductData.TYPE_FLOAT32);

            if (outputNdviSimple) {
                // for comparison purpose:
                // ndvi_simple = (rad10 - rad6)/(rad10 + rad6)
                ProductUtils.copyBand("ndvi", ndviSimpleProduct, "ndvi_from_radiance_6_and_10", targetProduct, true);
            }
        }

        // use now renovated Idepix:
        Map<String, Object> pixelClassParam = new HashMap<String, Object>(4);
        pixelClassParam.put("gaCopyRadiances", false);
        pixelClassParam.put("gaCopyAnnotations", false);
        pixelClassParam.put("gaComputeFlagsOnly", true);
        pixelClassParam.put("gaCloudBufferWidth", 3);
        pixelClassParam.put("gaUseL1bLandWaterFlag", false);
        pixelClassParam.put("gaLcCloudBuffer", true);
        pixelClassParam.put("gaApplyBlueDenseCloudAlgorithm", true);

        Product idepixProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoOp.class), pixelClassParam, sourceProduct);
        ProductUtils.copyFlagBands(idepixProduct, targetProduct, true);

        String cloudFreeExpression = "not l1_flags.INVALID " +
                "and not cloud_classif_flags.F_WATER " +
                "and not cloud_classif_flags.F_CLOUD " +
                "and not cloud_classif_flags.F_CLOUD_BUFFER " +
                "and not cloud_classif_flags.F_CLOUD_SHADOW " +
                "and cloud_classif_flags.F_CLEAR_LAND";

        BandMathsOp bandMathsOp3 = BandMathsOp.createBooleanExpressionBand(cloudFreeExpression, idepixProduct);
        cloudFreeBand = bandMathsOp3.getTargetProduct().getBandAt(0);

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        Tile time = targetTiles.get(timeBand);
        Tile targetValidFapar = targetTiles.get(validFaparBand);
        Tile targetValidLai = targetTiles.get(validLaiBand);
        Tile targetNdviKg = targetTiles.get(ndviKgBand);

        Tile srcValidFapar = getSourceTile(validFaparMask, targetRectangle);
        Tile srcValidLai = getSourceTile(validLaiMask, targetRectangle);
        Tile cloudFree = getSourceTile(cloudFreeBand, targetRectangle);

        Tile[] merisReflectanceTiles = null;
        if (computeNdviKg) {
            merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                merisReflectanceTiles[i] = getSourceTile(merisReflBands[i], targetRectangle);
            }
        }

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            final ProductData.UTC utcCurrentLine = ProductUtils.getScanLineTime(sourceProduct, y);
            double mjd = utcCurrentLine.getMJD();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                time.setSample(x, y, mjd);
                boolean isCloudFree = cloudFree.getSampleBoolean(x, y);
                targetValidFapar.setSample(x, y, srcValidFapar.getSampleBoolean(x, y) && isCloudFree);
                targetValidLai.setSample(x, y, srcValidLai.getSampleBoolean(x, y) && isCloudFree);

                if (computeNdviKg) {
                    if (isCloudFree) {
                        targetNdviKg.setSample(x, y, computeNdviKg(x, y, merisReflectanceTiles));
                    } else {
                        targetNdviKg.setSample(x, y, Float.NaN);
                    }
                }
            }
        }
    }

    private float computeNdviKg(int x, int y, Tile[] merisReflectanceTiles) {
        // for Kurt Guenther NDVI approach, we need reflectance from MERIS bands 6,7 and 10,12,13,14,15
        final float refl_6 = merisReflectanceTiles[5].getSampleFloat(x, y);
        final float refl_7 = merisReflectanceTiles[6].getSampleFloat(x, y);
        final float refl_10 = merisReflectanceTiles[9].getSampleFloat(x, y);
        final float refl_12 = merisReflectanceTiles[11].getSampleFloat(x, y);
        final float refl_13 = merisReflectanceTiles[12].getSampleFloat(x, y);
        final float refl_14 = merisReflectanceTiles[13].getSampleFloat(x, y);
        final float refl_15 = merisReflectanceTiles[14].getSampleFloat(x, y);

        return computeNdviKgValue(ALPLA_KG, BETA_KG, refl_6, refl_7, refl_10, refl_12, refl_13, refl_14, refl_15);
    }

    static float computeNdviKgValue(float alpha, float beta, float refl_6, float refl_7, float refl_10, float refl_12, float refl_13, float refl_14, float refl_15) {
        return (beta * (refl_10 + refl_12 + refl_13 + refl_14 + refl_15) - alpha * (refl_6 + refl_7)) /
                (beta * (refl_10 + refl_12 + refl_13 + refl_14 + refl_15) + alpha * (refl_6 + refl_7));
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobVegOp.class);
        }
    }
}

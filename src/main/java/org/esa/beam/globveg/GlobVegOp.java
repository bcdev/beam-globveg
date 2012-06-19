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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.idepix.operators.ComputeChainOp;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.util.Map;

/**
 * The combining operator for the GlobVeg project.
 */
@OperatorMetadata(alias = "GlobVeg",
                  authors = "Marco Zuehlke, Martin Boettcher",
                  copyright = "Brockmann Consult GmbH",
                  version = "1.0",
                  description = "combines fAPAR and LAI for GlobVeg")
public class GlobVegOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    private Band timeBand;
    private Band validFaparBand;
    private Band validLaiBand;
    private Band validFaparMask;
    private Band validLaiMask;
    private Band cloudFreeBand;

    @Override
    public void initialize() throws OperatorException {

        Product correctedL1b = GPF.createProduct("Meris.CorrectRadiometry", GPF.NO_PARAMS, sourceProduct);
        Product faparProduct = GPF.createProduct("Fapar", GPF.NO_PARAMS, correctedL1b);
        Product laiProduct = GPF.createProduct("ToaVeg", GPF.NO_PARAMS, correctedL1b);

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

        ComputeChainOp computeChainOp = new ComputeChainOp();
        computeChainOp.setSourceProduct(sourceProduct);
        computeChainOp.setParameter("algorithm", "GlobAlbedo");
        Product idepixProduct = computeChainOp.getTargetProduct();
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

        Tile srcValidFapar = getSourceTile(validFaparMask, targetRectangle);
        Tile srcValidLai = getSourceTile(validLaiMask, targetRectangle);
        Tile cloudFree = getSourceTile(cloudFreeBand, targetRectangle);

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            final ProductData.UTC utcCurrentLine = ProductUtils.getScanLineTime(sourceProduct, y);
            double mjd = utcCurrentLine.getMJD();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                time.setSample(x, y, mjd);
                boolean isCloudFree = cloudFree.getSampleBoolean(x, y);
                targetValidFapar.setSample(x, y, srcValidFapar.getSampleBoolean(x, y) && isCloudFree);
                targetValidLai.setSample(x, y, srcValidLai.getSampleBoolean(x, y) && isCloudFree);
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobVegOp.class);
        }
    }
}

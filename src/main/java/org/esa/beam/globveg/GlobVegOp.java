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

    @Override
    public void initialize() throws OperatorException {

        Product correctedL1b = GPF.createProduct("Meris.CorrectRadiometry", GPF.NO_PARAMS, sourceProduct);
        Product fapar = GPF.createProduct("Fapar", GPF.NO_PARAMS, correctedL1b);
        Product lai = GPF.createProduct("ToaVeg", GPF.NO_PARAMS, correctedL1b);

        Product targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(),
                                      sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyBand("FAPAR", fapar, targetProduct, true);
        ProductUtils.copyBand("LAI", lai, targetProduct, true);

        targetProduct.addBand("obs_time", ProductData.TYPE_FLOAT32); // MJD2000
        targetProduct.addBand("valid", ProductData.TYPE_INT8);

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        // TODO compute time
        // TODO clouds
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobVegOp.class);
        }
    }
}

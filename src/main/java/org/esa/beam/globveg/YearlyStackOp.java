package org.esa.beam.globveg;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.globveg.util.Constants;
import org.esa.beam.globveg.util.GlobvegUtils;
import org.esa.beam.util.ProductUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Operator to build yearly stacks of biweekly Globveg products
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Globveg.Yearlystack", version = "1.0",
                  authors = "Olaf Danne",
                  copyright = "(c) 2013 Brockmann Consult",
                  internal = true,
                  description = "Operator to build yearly stacks of biweekly Globveg products.")
public class YearlyStackOp extends Operator {
    public static final String VERSION = "1.0-SNAPSHOT";

    @Parameter(defaultValue = "", description = "Input data directory")
    private File inputDataDir;

    @Parameter(defaultValue = "", description = "Output data directory")
    private File outputDataDir;

    @Parameter(defaultValue = "", description = "The year to process")
    private String year;

    @Parameter(valueSet = {"10-iberia", "12-southafrica", "13-west-sudanian-savanna", "15-caatinga", "20-australia"},
               description = "The site to process.")
    private String globvegSite;

    @Parameter(description = "The tile to process (to be given as 'hYYvXX')")
    private String globvegTile;

    @Parameter(defaultValue = "1.0f", description = "The scale factor of the target product")
    private float scaleFactor;

    private Product[] globvegSourceProducts;

    @Override
    public void initialize() throws OperatorException {
        globvegSourceProducts = getGlobvegSourceProducts();

        Arrays.sort(globvegSourceProducts, new ProductNameComparator());

        final Product yearlyGlobvegProduct = createYearlyProduct();

        for (Product product : globvegSourceProducts) {
            for (Band b : product.getBands()) {
                final String targetBandName = getTargetBandName(b.getName(), product.getName());
                yearlyGlobvegProduct.addBand(targetBandName, b.getDataType());
                // test: downscale
                yearlyGlobvegProduct.getBand(targetBandName).setSourceImage(GlobvegUtils.scale(b.getSourceImage(), scaleFactor));
                yearlyGlobvegProduct.getBand(targetBandName).setNoDataValue(b.getNoDataValue());
                yearlyGlobvegProduct.getBand(targetBandName).setNoDataValueUsed(true);
            }
        }
        GlobvegUtils.addPatternToAutoGrouping(yearlyGlobvegProduct, "fapar");
        GlobvegUtils.addPatternToAutoGrouping(yearlyGlobvegProduct, "lai");
        GlobvegUtils.addPatternToAutoGrouping(yearlyGlobvegProduct, "ndvi_kg");

        setTargetProduct(yearlyGlobvegProduct);
    }

    private String getTargetBandName(String prefix, String name) {
        // we want as band names
        // 'xxx_jan01' for product name e.g. 'meris-globveg-20050101-v04h17-1.0.nc'
        // 'xxx_jan16' for product name e.g. 'meris-globveg-20050116-v04h17-1.0.nc'
        // etc.
        final String MM = name.substring(18, 20);
        final int monthIndex = Integer.parseInt(MM) - 1;
        final String suffix = name.substring(20, 22);

        return prefix + "_" + Constants.MONTHS[monthIndex] + suffix;
    }

    private Product createYearlyProduct() {
        final int width = (int) (globvegSourceProducts[0].getSceneRasterWidth() * scaleFactor);
        final int height = (int) (globvegSourceProducts[0].getSceneRasterHeight() * scaleFactor);

        Product yearlyProduct = new Product("DIVERSITY_GLOBVEG",
                                            "DIVERSITY_GLOBVEG",
                                            width,
                                            height);
        ProductUtils.copyGeoCoding(globvegSourceProducts[0], yearlyProduct);

        return yearlyProduct;
    }

    private Product[] getGlobvegSourceProducts() {
        final FileFilter globvegHalfmonthlyDirsFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().startsWith(year) && file.getName().endsWith("-nc");
            }
        };

        final String globvegDir = inputDataDir + File.separator + globvegSite + File.separator + year;
        final File[] globvegHalfmonthlyDirs = (new File(globvegDir)).listFiles(globvegHalfmonthlyDirsFilter);

        List<Product> globvegSourceProductsList = new ArrayList<Product>();

        int productIndex = 0;
        for (File globvegHalfmonthlyDir : globvegHalfmonthlyDirs) {
            String globvegInputFileName = globvegHalfmonthlyDir.getAbsolutePath() + File.separator +
                    "meris-globveg-" + globvegHalfmonthlyDir.getName().substring(0, 8) + "-" + globvegTile + "-1.0.nc";
            try {
                final Product product = ProductIO.readProduct(globvegInputFileName);
                if (product != null) {
                    globvegSourceProductsList.add(product);
                    productIndex++;
                }
            } catch (IOException e) {
                System.err.println("Warning: Globveg netcdf file in halfmonthly directory '" +
                                           globvegHalfmonthlyDir.getName() + "' missing or could not be read - skipping.");
            }
        }

        if (productIndex == 0) {
            System.out.println("No GlobVeg source products found for region " + globvegSite +
                                       ", year " + year + ", tile " + globvegTile + " - nothing to do.");
        }

        return globvegSourceProductsList.toArray(new Product[globvegSourceProductsList.size()]);
    }

    private class ProductNameComparator implements Comparator<Product> {
        @Override
        public int compare(Product o1, Product o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(YearlyStackOp.class);
        }
    }
}

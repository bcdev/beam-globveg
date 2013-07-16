package org.esa.beam.globveg;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.beam.gpf.operators.standard.WriteOp;
import org.esa.beam.util.ProductUtils;

import java.awt.image.RenderedImage;
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

    @Parameter(valueSet = {"10-iberia",
                            "12-southafrica",
                            "13-west-sudanian-savanna",
                            "13-west-sudanian-savanna_west",
                            "13-west-sudanian-savanna_east",
                            "15-caatinga",
                            "20-australia"},
            description = "The site to process.")
    private String globvegSite;

    @Parameter(defaultValue = "1.0f", description = "The scale factor of the target product")
    private float scaleFactor;

    private Product[] globvegSourceProducts;

    @Override
    public void initialize() throws OperatorException {
        globvegSourceProducts = getGlobvegSourceProducts();

        Arrays.sort(globvegSourceProducts, new ProductNameComparator());

        final Product yearlyGlobvegFaparProduct = createYearlyProduct("FAPAR");
        final Product yearlyGlobvegLaiProduct = createYearlyProduct("LAI");
        final Product yearlyGlobvegNdviProduct = createYearlyProduct("NDVI");
        final Product yearlyGlobvegMetaProduct = createYearlyProduct("META");

        for (Product product : globvegSourceProducts) {
            for (Band b : product.getBands()) {
                final String targetBandName = getTargetBandName(b.getName(), product.getName());

                RenderedImage targetImage;
                if (scaleFactor != 1.0f) {
                    targetImage = GlobvegUtils.scale(b.getSourceImage(), scaleFactor);
                } else {
                    targetImage = b.getSourceImage();
                }

                if (b.getName().equalsIgnoreCase("fapar")) {
                    yearlyGlobvegFaparProduct.addBand(targetBandName, b.getDataType());
                    yearlyGlobvegFaparProduct.getBand(targetBandName).setSourceImage(targetImage);
                    yearlyGlobvegFaparProduct.getBand(targetBandName).setNoDataValue(b.getNoDataValue());
                    yearlyGlobvegFaparProduct.getBand(targetBandName).setNoDataValueUsed(true);
                } else if (b.getName().equalsIgnoreCase("lai")) {
                    yearlyGlobvegLaiProduct.addBand(targetBandName, b.getDataType());
                    yearlyGlobvegLaiProduct.getBand(targetBandName).setSourceImage(targetImage);
                    yearlyGlobvegLaiProduct.getBand(targetBandName).setNoDataValue(b.getNoDataValue());
                    yearlyGlobvegLaiProduct.getBand(targetBandName).setNoDataValueUsed(true);
                } else if (b.getName().equalsIgnoreCase("ndvi_kg_max")) {
                    yearlyGlobvegNdviProduct.addBand(targetBandName, b.getDataType());
                    yearlyGlobvegNdviProduct.getBand(targetBandName).setSourceImage(targetImage);
                    yearlyGlobvegNdviProduct.getBand(targetBandName).setNoDataValue(b.getNoDataValue());
                    yearlyGlobvegNdviProduct.getBand(targetBandName).setNoDataValueUsed(true);
                } else if (b.getName().equalsIgnoreCase("num_obs")) {
                    yearlyGlobvegMetaProduct.addBand(targetBandName, b.getDataType());
                    yearlyGlobvegMetaProduct.getBand(targetBandName).setSourceImage(targetImage);
                    yearlyGlobvegMetaProduct.getBand(targetBandName).setNoDataValue(b.getNoDataValue());
                    yearlyGlobvegMetaProduct.getBand(targetBandName).setNoDataValueUsed(true);
                }
            }
        }

        final String faparTargetFileName = outputDataDir + File.separator + "L3_" + year + "_" + globvegSite + "_FAPAR.tif";
        final File faparTargetFile = new File(faparTargetFileName);
        final WriteOp faparWriteOp = new WriteOp(yearlyGlobvegFaparProduct, faparTargetFile, "GeoTIFF");
//        final WriteOp faparWriteOp = new WriteOp(yearlyGlobvegFaparProduct, faparTargetFile, "NetCDF4-CF");
        faparWriteOp.writeProduct(ProgressMonitor.NULL);

        final String laiTargetFileName = outputDataDir + File.separator + "L3_" + year + "_" + globvegSite + "_LAI.tif";
        final File laiTargetFile = new File(laiTargetFileName);
        final WriteOp laiWriteOp = new WriteOp(yearlyGlobvegLaiProduct, laiTargetFile, "GeoTIFF");
        laiWriteOp.writeProduct(ProgressMonitor.NULL);

        final String ndviTargetFileName = outputDataDir + File.separator + "L3_" + year + "_" + globvegSite + "_NDVI.tif";
        final File ndviTargetFile = new File(ndviTargetFileName);
        final WriteOp ndviWriteOp = new WriteOp(yearlyGlobvegNdviProduct, ndviTargetFile, "GeoTIFF");
        ndviWriteOp.writeProduct(ProgressMonitor.NULL);

        final String metaTargetFileName = outputDataDir + File.separator + "L3_" + year + "_" + globvegSite + "_meta.tif";
        final File metaTargetFile = new File(metaTargetFileName);
        final WriteOp metaWriteOp = new WriteOp(yearlyGlobvegMetaProduct, metaTargetFile, "GeoTIFF");
        metaWriteOp.writeProduct(ProgressMonitor.NULL);

        final Product dummyTargetProduct = new Product("a", "b", 0, 0);
        setTargetProduct(dummyTargetProduct);
    }

    private String getTargetBandName(String prefix, String name) {
        // we want as band names
        // 'xxx_jan01' for product name e.g. 'L3_2010-01-01_2010-01-10.nc'
        // 'xxx_jan16' for product name e.g. 'L3_2010-01-16_2010-01-25.nc'
        // 'xxx_feb01' for product name e.g. 'L3_2010-02-01_2010-02-10.nc'
        // etc.
        final String MM = name.substring(8, 10);
        final int monthIndex = Integer.parseInt(MM) - 1;
        final String suffix = name.substring(11, 13);

        return prefix + "_" + Constants.MONTHS[monthIndex] + suffix;
    }

    private Product createYearlyProduct(String productType) {
        final int width = (int) (globvegSourceProducts[0].getSceneRasterWidth() * scaleFactor);
        final int height = (int) (globvegSourceProducts[0].getSceneRasterHeight() * scaleFactor);

        Product yearlyProduct = new Product("DIVERSITY_GLOBVEG_" + productType,
                "DIVERSITY_GLOBVEG_" + productType,
                width,
                height);

        ProductUtils.copyGeoCoding(globvegSourceProducts[0], yearlyProduct);

        return yearlyProduct;
    }

    private Product[] getGlobvegSourceProducts() {
        final FileFilter globvegProductsFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().startsWith("L3_" + year) && file.getName().endsWith(".nc");
            }
        };

//        final String globvegDir = inputDataDir + File.separator + globvegSite + File.separator + year;
        // we expect as inputDataDir the directory where all files from one year for this region are located
        final String globvegDir = inputDataDir.getAbsolutePath();

        final File[] globvegSourceProductFiles = (new File(globvegDir)).listFiles(globvegProductsFilter);
        List<Product> globvegSourceProductsList = new ArrayList<Product>();

        int productIndex = 0;
        if (globvegSourceProductFiles != null && globvegSourceProductFiles.length > 0) {
            for (File globvegSourceProductFile : globvegSourceProductFiles) {
                try {
                    final Product product = ProductIO.readProduct(globvegSourceProductFile.getAbsolutePath());
                    if (product != null) {
                        globvegSourceProductsList.add(product);
                        productIndex++;
                    }
                } catch (IOException e) {
                    System.err.println("WARNING: Globveg L3 netcdf file '" +
                                               globvegSourceProductFile.getName() + "' could not be read - skipping.");
                }
            }
        }
        if (productIndex == 0) {
            System.out.println("No GlobVeg source products found for region " + globvegSite +
                                       ", year " + year + " - nothing to do.");
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

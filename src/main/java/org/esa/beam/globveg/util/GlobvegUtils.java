package org.esa.beam.globveg.util;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Product;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;

/**
 * Globveg Utility Class
 *
 * @author olafd
 */
public class GlobvegUtils {

    public static void addPatternToAutoGrouping(Product targetProduct, String groupPattern) {
        Product.AutoGrouping autoGrouping = targetProduct.getAutoGrouping();
        String stringPattern = autoGrouping != null ? autoGrouping.toString() + ":" + groupPattern : groupPattern;
        targetProduct.setAutoGrouping(stringPattern);
    }

    public static RenderedOp scale(MultiLevelImage sourceImage, float scaleFactor) {
        // here we scale the image if necessary (scaleFactor != 1)
        return ScaleDescriptor.create(sourceImage,
                scaleFactor,
                scaleFactor,
                0.0f, 0.0f,
                Interpolation.getInstance(
                        Interpolation.INTERP_NEAREST),
                null);
    }
}

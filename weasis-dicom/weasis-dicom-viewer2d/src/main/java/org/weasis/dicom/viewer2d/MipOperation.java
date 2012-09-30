package org.weasis.dicom.viewer2d;

import java.awt.image.RenderedImage;
import java.util.Comparator;
import java.util.Iterator;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.AbstractOperation;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.dicom.codec.DicomImageElement;

public class MipOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(MipOperation.class);

    public static final String name = "mip_op";
    public static final ActionW MIP = new ActionW("MIP", "mip", 0, 0, null);
    public static final ActionW MIP_MIN_SLICE = new ActionW("Min Slice: ", "mip_min", 0, 0, null);
    public static final ActionW MIP_MAX_SLICE = new ActionW("Max Slice: ", "mip_max", 0, 0, null);

    public enum Type {
        MIN("min-MIP"), MAX("MIP");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    };

    @Override
    public String getOperationName() {
        return name;
    }

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Type mipType = (Type) imageOperation.getActionValue(MipOperation.MIP.cmd());
        Integer min = (Integer) imageOperation.getActionValue(MipOperation.MIP_MIN_SLICE.cmd());
        Integer max = (Integer) imageOperation.getActionValue(MipOperation.MIP_MAX_SLICE.cmd());
        if (mipType == null || min == null || max == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (max - min < 3) {
            result = source;
        } else {
            PlanarImage curImage = null;
            MediaSeries<DicomImageElement> series = imageOperation.getSeries();
            if (series != null) {

                final String operator = mipType.name().toLowerCase();
                SeriesComparator sort = (SeriesComparator) imageOperation.getActionValue(ActionW.SORTSTACK.cmd());
                Boolean reverse = (Boolean) imageOperation.getActionValue(ActionW.INVERSESTACK.cmd());
                Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
                Filter filter = (Filter) imageOperation.getActionValue(ActionW.FILTERED_SERIES.cmd());
                Iterable<DicomImageElement> medias = series.getMedias(filter, sortFilter);

                synchronized (medias) {
                    Iterator<DicomImageElement> iter = medias.iterator();
                    int startIndex = min - 1;
                    int k = 0;
                    if (startIndex > 0) {
                        while (iter.hasNext()) {
                            DicomImageElement dcm = iter.next();
                            if (k >= startIndex) {
                                curImage = dcm.getImage();
                                break;
                            }
                            k++;
                        }
                    } else {
                        if (iter.hasNext()) {
                            DicomImageElement dcmCur = iter.next();
                            curImage = dcmCur.getImage();
                        }
                    }

                    int stopIndex = max - 1;
                    if (curImage != null) {
                        while (iter.hasNext()) {
                            DicomImageElement dcm = iter.next();
                            PlanarImage img = dcm.getImage();
                            curImage = arithmeticOperation(operator, curImage, img);
                            if (k >= stopIndex) {
                                break;
                            }
                            k++;
                        }
                    }
                }
            }
            result = curImage == null ? source : curImage;
        }
        return result;
    }

    public static PlanarImage arithmeticOperation(String operation, PlanarImage img1, PlanarImage img2) {
        ParameterBlockJAI pb2 = new ParameterBlockJAI(operation);
        pb2.addSource(img1);
        pb2.addSource(img2);
        return JAI.create(operation, pb2);
    }
}

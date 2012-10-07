package org.weasis.dicom.viewer2d.mpr;

import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.View2d;

public class MprView extends View2d {
    public enum Type {
        AXIAL, CORONAL, SAGITTAL
    };

    private Type type;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.type = Type.AXIAL;
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        // Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
        // head direction)
        // TODO This option should be fixed
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.slicePosition);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type == null ? Type.AXIAL : type;
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {

        super.setSeries(series, selectedDicom);

        // DicomImageElement dcm = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        // if (dcm != null) {
        // double[] val = (double[]) dcm.getTagValue(TagW.ImageOrientationPatient);
        // double[] stackOrientation = ImageOrientation.computeNormalVectorOfPlan(val);
        // if (Type.CORONAL.equals(type)) {
        //
        // }
        // }

    }

    @Override
    protected void setImage(DicomImageElement img, boolean bestFit) {
        super.setImage(img, bestFit);
    }

    public void anonymizeVolumeNotAxial() {
        int size = this.series.size(null);
        DicomImageElement midSeries = this.series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
        boolean readVert = false;

        double[] v = (double[]) midSeries.getTagValue(TagW.ImageOrientationPatient);
        String rowAxis = ImageOrientation.getMajorAxisFromPatientRelativeDirectionCosine(v[0], v[1], v[2]);
        String colAxis = ImageOrientation.getMajorAxisFromPatientRelativeDirectionCosine(v[3], v[4], v[5]);
        TransposeType rotate = null;
        // Coronal
        if (((rowAxis.equals("L") && colAxis.equals("F")) || (rowAxis.equals("R")) && colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            rotate = TransposeDescriptor.ROTATE_180;
        } else if ((rowAxis.equals("H") || rowAxis.equals("F")) && (colAxis.equals("R") || colAxis.equals("L"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            readVert = true;
            if ((rowAxis.equals("H") && colAxis.equals("L")) || (rowAxis.equals("F") && colAxis.equals("R"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                rotate = TransposeDescriptor.ROTATE_180;
            }
        }

        // Sagittal
        else if (rowAxis.equals("A") && (colAxis.equals("F") || colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            rotate = TransposeDescriptor.ROTATE_270;
        } else if (rowAxis.equals("P") && (colAxis.equals("F") || colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            rotate = TransposeDescriptor.ROTATE_90;
        } else if (colAxis.equals("P") && (rowAxis.equals("H") || rowAxis.equals("F"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            readVert = true;
            rotate = TransposeDescriptor.ROTATE_90;
        } else if (colAxis.equals("A") && (rowAxis.equals("H") || rowAxis.equals("F"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            readVert = true;
            rotate = TransposeDescriptor.ROTATE_270;
        }

    }
}

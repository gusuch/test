package org.weasis.dicom.viewer2d;

import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;

import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.dicom.codec.DicomImageElement;

public class MipView extends View2d {

    public enum Type {
        NONE("None"), MIN("min-MIP"), MAX("MIP");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    };

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();

        actionsInView.put(ActionW.MIP.cmd(), MipToolBar.Type.NONE);
        actionsInView.put(ActionW.MIP_THICKNESS.cmd(), 1);

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        final String command = evt.getPropertyName();
        final Object val = evt.getNewValue();
        if (command.equals(ActionW.MIP.cmd())) {
            actionsInView.put(ActionW.MIP.cmd(), val);
            applyMipParameters();
        } else if (command.equals(ActionW.MIP_THICKNESS.cmd())) {
            actionsInView.put(ActionW.MIP_THICKNESS.cmd(), val);
            applyMipParameters();
        }
    }

    public void applyMipParameters() {

        OperationsManager manager = (OperationsManager) actionsInView.get(ActionW.PREPROCESSING.cmd());
        if (manager == null) {
            manager = new OperationsManager(new ImageOperation() {

                @Override
                public RenderedImage getSourceImage() {
                    ImageElement image = getImage();
                    if (image == null) {
                        return null;
                    }
                    return image.getImage(null);
                }

                @Override
                public ImageElement getImage() {
                    return MipView.this.getImage();
                }

                @Override
                public Object getActionValue(String action) {
                    if (action == null) {
                        return null;
                    }
                    return actionsInView.get(action);
                }

                @Override
                public MediaSeries getSeries() {
                    return MipView.this.getSeries();
                }
            });
            manager.addImageOperationAction(new MipOperation());
            actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
            imageLayer.setPreprocessing(manager);
        }
        imageLayer.updateAllImageOperations();
        // TODO update statistics
    }
}

package org.weasis.dicom.viewer2d;

import java.awt.image.RenderedImage;
import java.util.HashMap;

import javax.swing.JComboBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.util.WtoolBar;

public class MipToolBar<DicomImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(MipToolBar.class);

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

    public MipToolBar() {
        super("MIP Toolbar", TYPE.tool);
        ActionState mip = EventManager.getInstance().getAction(ActionW.MIP);
        if (mip instanceof ComboItemListener) {
            final ComboItemListener mipAction = (ComboItemListener) mip;
            final JComboBox mipsSlid = mipAction.createCombo(100);
            add(mipsSlid);
        }
        ActionState mipThick = EventManager.getInstance().getAction(ActionW.MIP_THICKNESS);
        if (mipThick instanceof SliderChangeListener) {
            final SliderChangeListener mipAction = (SliderChangeListener) mipThick;
            final JSliderW mipsSlid = mipAction.createSlider(4, false);
            add(mipsSlid);
        }
    }

    public static void applyMipParameters(final View2d view2d, RenderedImageLayer imageLayer,
        final HashMap<String, Object> actionsInView) {

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
                    return view2d.getImage();
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
                    return view2d.getSeries();
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

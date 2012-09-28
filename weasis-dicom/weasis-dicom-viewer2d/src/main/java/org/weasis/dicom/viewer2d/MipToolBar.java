package org.weasis.dicom.viewer2d;

import javax.swing.JComboBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
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

}

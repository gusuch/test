package org.weasis.dicom.viewer2d;

import java.awt.Graphics2D;
import java.awt.image.RenderedImage;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.dicom.codec.DicomImageElement;

public class MipView extends View2d {

    private static final ViewButton MIP_BUTTON = new ViewButton(new MipPopup(), new ImageIcon(
        DefaultView2d.class.getResource("/icon/22x22/sequence.png")));

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        viewButtons.add(MIP_BUTTON);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();

        actionsInView.put(MipOperation.MIP.cmd(), MipOperation.Type.MAX);
        int index = 7;
        ActionState sequence = eventManager.getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            cineAction.stop();
            int val = cineAction.getValue();
            if (val > 7) {
                index = val;
            }
            // TODO handle scroll position with index
            // actionsInView.put(ActionW.SCROLL_SERIES.cmd(), index);
        }
        // Force to extend VOI LUT to pixel allocated
        actionsInView.put(DicomImageElement.FILL_OUTSIDE_LUT, true);
        actionsInView.put(MipOperation.MIP_MIN_SLICE.cmd(), index - 7);
        actionsInView.put(MipOperation.MIP_MAX_SLICE.cmd(), index + 7);
    }

    @Override
    protected void drawExtendedAtions(Graphics2D g2d) {
        Icon icon = MIP_BUTTON.getIcon();
        int x = getWidth() - icon.getIconWidth() - 5;
        int y = (int) ((getHeight() - 1) * 0.5);
        MIP_BUTTON.x = x;
        MIP_BUTTON.y = y;
        icon.paintIcon(this, g2d, x, y);
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
            // TODO PREPROCESSING conflict with PR, handle globally?
            actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
            imageLayer.setPreprocessing(manager);
        }
        // TODO check images have similar modality and VOI LUT, W/L, LUT shape...
        imageLayer.updateAllImageOperations();
        DicomImageElement image = imageLayer.getSourceImage();
        if (image != null) {
            // Update statistics
            List<Graphic> list = (List<Graphic>) image.getTagValue(TagW.MeasurementGraphics);
            if (list != null) {
                for (Graphic graphic : list) {
                    graphic.updateLabel(true, this);
                }
            }
        }
    }
}

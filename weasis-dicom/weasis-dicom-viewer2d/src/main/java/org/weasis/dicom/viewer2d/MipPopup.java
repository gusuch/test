package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.MipOperation.Type;

public class MipPopup implements ShowPopup {
    private final Logger LOGGER = LoggerFactory.getLogger(MipPopup.class);

    /**
     * @wbp.parser.entryPoint
     */
    public JDialog buildDialog(final MipView view) {
        if (view == null) {
            return null;
        }
        final JDialog dialog =
            new JDialog(WinUtil.getParentWindow(view), "MIP Options", ModalityType.APPLICATION_MODAL);
        final Container panel_1 = dialog.getContentPane();
        panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.Y_AXIS));

        final JPanel framePanel = new JPanel();
        final ButtonGroup ratioGroup = new ButtonGroup();

        JRadioButton rdbtnMinProjection = new JRadioButton("Min Projection");
        framePanel.add(rdbtnMinProjection);
        JRadioButton rdbtnMaxProjection = new JRadioButton("Max Projection");
        framePanel.add(rdbtnMaxProjection);
        panel_1.add(framePanel);
        ratioGroup.add(rdbtnMinProjection);
        ratioGroup.add(rdbtnMaxProjection);
        MipOperation.Type type = (Type) view.getActionValue(MipOperation.MIP.cmd());
        if (MipOperation.Type.MIN.equals(type)) {
            rdbtnMinProjection.setSelected(true);
        } else {
            rdbtnMaxProjection.setSelected(true);
        }
        rdbtnMinProjection.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipOperation.MIP.cmd(), MipOperation.Type.MIN);
                        view.applyMipParameters();
                    }
                }
            }
        });
        rdbtnMaxProjection.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JRadioButton) {
                    JRadioButton btn = (JRadioButton) e.getSource();
                    if (btn.isSelected()) {
                        view.setActionsInView(MipOperation.MIP.cmd(), MipOperation.Type.MAX);
                        view.applyMipParameters();
                    }
                }
            }
        });

        ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            final JSliderW frameSliderMin = createSlider("Min Slice", 4, false, cineAction.getModel());
            panel_1.add(frameSliderMin.getParent());
            final JSliderW frameSliderMax = createSlider("Max Slice", 4, false, cineAction.getModel());
            panel_1.add(frameSliderMax.getParent());
            frameSliderMin.setValue((Integer) view.getActionValue(MipOperation.MIP_MIN_SLICE.cmd()));
            frameSliderMax.setValue((Integer) view.getActionValue(MipOperation.MIP_MAX_SLICE.cmd()));

            frameSliderMin.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSliderW slider = (JSliderW) e.getSource();
                    updateSliderProoperties(slider, MipOperation.MIP_MIN_SLICE.getTitle() + slider.getValue());
                    if (!slider.getValueIsAdjusting()) {
                        view.setActionsInView(MipOperation.MIP_MIN_SLICE.cmd(), slider.getValue());
                        view.applyMipParameters();
                    }
                }
            });
            frameSliderMax.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSliderW slider = (JSliderW) e.getSource();
                    updateSliderProoperties(slider, MipOperation.MIP_MAX_SLICE.getTitle() + slider.getValue());
                    if (!slider.getValueIsAdjusting()) {
                        view.setActionsInView(MipOperation.MIP_MAX_SLICE.cmd(), slider.getValue());
                        view.applyMipParameters();
                    }
                }
            });
        }
        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.TRAILING);
        panel.setBorder(new EmptyBorder(20, 15, 10, 15));
        dialog.getContentPane().add(panel);

        JButton btnExitMipMode = new JButton("Exit MIP Mode");
        btnExitMipMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImageViewerPlugin<DicomImageElement> container = view.getEventManager().getSelectedView2dContainer();
                container.setSelectedAndGetFocus();
                View2d newView2d = new View2d(view.getEventManager());
                newView2d.registerDefaultListeners();
                newView2d.setSeries(view.getSeries());
                container.replaceView(view, newView2d);
                dialog.dispose();
            }
        });
        panel.add(btnExitMipMode);

        Component horizontalStrut = Box.createHorizontalStrut(20);
        panel.add(horizontalStrut);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        panel.add(btnClose);
        return dialog;

    }

    public JSliderW createSlider(String title, int labelDivision, boolean displayOnlyValue,
        DefaultBoundedRangeModel model) {
        final JPanel palenSlider1 = new JPanel();
        palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
        palenSlider1.setBorder(new TitledBorder(title));
        JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum(), model.getValue());
        slider.setLabelDivision(labelDivision);
        slider.setDisplayOnlyValue(displayOnlyValue);
        slider.setPaintTicks(true);
        palenSlider1.add(slider);
        if (labelDivision > 0) {
            slider.setPaintLabels(true);
            SliderChangeListener.setSliderLabelValues(slider, model.getMinimum(), model.getMaximum());
        }
        return slider;
    }

    public static void updateSliderProoperties(JSliderW slider, String title) {
        JPanel panel = (JPanel) slider.getParent();
        if (!slider.isDisplayOnlyValue() && panel != null && panel.getBorder() instanceof TitledBorder) {
            ((TitledBorder) panel.getBorder()).setTitle(title);
            panel.repaint();
        } else {
            slider.setToolTipText(title);
        }
    }

    @Override
    public void showPopup(Component invoker, int x, int y) {
        if (invoker instanceof MipView) {
            JDialog dialog = buildDialog((MipView) invoker);
            dialog.pack();
            dialog.setLocation(x, y);
            dialog.setVisible(true);
        }
    }
}

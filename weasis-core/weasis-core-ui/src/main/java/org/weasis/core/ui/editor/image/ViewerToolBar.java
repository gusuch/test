/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.ListDataEvent;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ViewerToolBar<E extends ImageElement> extends WtoolBar implements ActionListener {

    public static final ActionW[] actionsButtons =
    // ActionW.DRAW,
        { ActionW.PAN, ActionW.WINLEVEL, ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION, ActionW.MEASURE,
            ActionW.CONTEXTMENU };
    public static final ActionW[] actionsScroll = { ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION };
    public static final Icon MouseLeftIcon =
        new ImageIcon(MouseActions.class.getResource("/icon/32x32/mouse-left.png")); //$NON-NLS-1$
    public static final Icon MouseRightIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-right.png")); //$NON-NLS-1$
    public static final Icon MouseMiddleIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-middle.png")); //$NON-NLS-1$
    public static final Icon MouseWheelIcon = new ImageIcon(
        MouseActions.class.getResource("/icon/32x32/mouse-wheel.png")); //$NON-NLS-1$

    protected final ImageViewerEventManager<E> eventManager;
    private final DropDownButton mouseLeft;
    private DropDownButton mouseMiddle;
    private DropDownButton mouseRight;
    private DropDownButton mouseWheel;
    private DropDownButton synchButton;
    private final MeasureToolBar<E> measureToolBar;

    public ViewerToolBar(final ImageViewerEventManager<E> eventManager) {
        super("Viewer2d Main Bar", TYPE.main); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
        measureToolBar = new MeasureToolBar<E>(eventManager);

        MouseActions actions = eventManager.getMouseActions();
        int active = actions.getActiveButtons();
        mouseLeft = buildMouseButton(actions, MouseActions.LEFT);
        mouseLeft
            .setToolTipText(Messages.getString("ViewerToolBar.change") + " " + Messages.getString("ViewerToolBar.m_action")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        if (((active & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK)) {
            add(mouseLeft);
        }
        if (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK)) {
            add(mouseMiddle = buildMouseButton(actions, MouseActions.MIDDLE));
        }
        if (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK)) {
            add(mouseRight = buildMouseButton(actions, MouseActions.RIGHT));
        }

        if (((active & MouseActions.SCROLL_MASK) == MouseActions.SCROLL_MASK)) {
            mouseWheel =
                new DropDownButton(MouseActions.WHEEL, buildMouseIcon(MouseActions.WHEEL,
                    actions.getAction(MouseActions.WHEEL))) {

                    @Override
                    protected JPopupMenu getPopupMenu() {
                        return getPopupMenuScroll(this);
                    }
                };
            mouseWheel.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1
            add(mouseWheel);
        }

        if (active > 1) {
            addSeparator(WtoolBar.SEPARATOR_2x24);
        }

        final DropDownButton layout = new DropDownButton("layout", new DropButtonIcon(new ImageIcon(MouseActions.class //$NON-NLS-1$
            .getResource("/icon/32x32/layout.png")))) { //$NON-NLS-1$

                @Override
                protected JPopupMenu getPopupMenu() {
                    return getLayoutPopupMenuButton(this);
                }
            };
        layout.setToolTipText(Messages.getString("ViewerToolBar.layout")); //$NON-NLS-1$

        WProperties p = BundleTools.SYSTEM_PREFERENCES;
        boolean separtor = false;
        if (p.getBooleanProperty("weasis.toolbar.layoutbouton", true)) {
            add(layout);
            separtor = true;
        }

        if (p.getBooleanProperty("weasis.toolbar.synchbouton", true)) {
            add(Box.createRigidArea(new Dimension(5, 0)));

            synchButton = buildSynchButton();
            add(synchButton);
            separtor = true;
        }
        if (separtor) {
            addSeparator(WtoolBar.SEPARATOR_2x24);
        }
        final JButton jButtonActualZoom =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-original.png"))); //$NON-NLS-1$
        jButtonActualZoom.setToolTipText(Messages.getString("ViewerToolBar.zoom_1")); //$NON-NLS-1$
        jButtonActualZoom.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ActionState zoom = eventManager.getAction(ActionW.ZOOM);
                if (zoom instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoom).setValue(eventManager.viewScaleToSliderValue(1.0));
                }
            }
        });
        add(jButtonActualZoom);

        final JButton jButtonBestFit =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-bestfit.png"))); //$NON-NLS-1$
        jButtonBestFit.setToolTipText(Messages.getString("ViewerToolBar.zoom_b")); //$NON-NLS-1$
        jButtonBestFit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
                // value is adjusted by the BoundedRangeModel
                eventManager.firePropertyChange(ActionW.ZOOM.cmd(), null, 0.0);
                AuditLog.LOGGER.info("action:{} val:0.0", ActionW.ZOOM.cmd()); //$NON-NLS-1$
            }
        });
        add(jButtonBestFit);

        final JToggleButton jButtonLens =
            new JToggleButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-lens.png"))); //$NON-NLS-1$
        jButtonLens.setToolTipText(Messages.getString("ViewerToolBar.show_lens")); //$NON-NLS-1$
        ActionState lens = eventManager.getAction(ActionW.LENS);
        if (lens instanceof ToggleButtonListener) {
            ((ToggleButtonListener) lens).registerComponent(jButtonLens);
        }
        add(jButtonLens);

        displayMeasureToobar();
    }

    private DropDownButton buildMouseButton(MouseActions actions, String actionLabel) {
        String action = actions.getAction(actionLabel);
        final DropDownButton button = new DropDownButton(actionLabel, buildMouseIcon(actionLabel, action)) {

            @Override
            protected JPopupMenu getPopupMenu() {
                return getPopupMenuButton(this);
            }
        };
        button.setActionCommand(action);
        button.setToolTipText(Messages.getString("ViewerToolBar.change")); //$NON-NLS-1$
        return button;
    }

    public DropDownButton getMouseLeft() {
        return mouseLeft;
    }

    private JPopupMenu getLayoutPopupMenuButton(DropDownButton dropDownButton) {
        ActionState layout = eventManager.getAction(ActionW.LAYOUT);
        JPopupMenu popupMouseButtons = new JPopupMenu();
        if (layout instanceof ComboItemListener) {
            JMenu menu = ((ComboItemListener) layout).createUnregisteredRadioMenu("layout"); //$NON-NLS-1$
            popupMouseButtons.setInvoker(dropDownButton);
            Component[] cps = menu.getMenuComponents();
            for (int i = 0; i < cps.length; i++) {
                popupMouseButtons.add(cps[i]);
            }
        }
        return popupMouseButtons;
    }

    public MeasureToolBar getMeasureToolBar() {
        return measureToolBar;
    }

    private JPopupMenu getPopupMenuButton(DropDownButton dropButton) {
        String type = dropButton.getType();
        String action = eventManager.getMouseActions().getAction(type);
        JPopupMenu popupMouseButtons = new JPopupMenu(type);
        popupMouseButtons.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        for (int i = 0; i < actionsButtons.length; i++) {
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(actionsButtons[i].getTitle(), actionsButtons[i].getIcon(), actionsButtons[i]
                    .cmd().equals(action));
            radio.setActionCommand(actionsButtons[i].cmd());
            radio.addActionListener(this);
            if (MouseActions.LEFT.equals(type)) {
                radio.setAccelerator(KeyStroke.getKeyStroke(actionsButtons[i].getKeyCode(),
                    actionsButtons[i].getModifier()));
            }
            popupMouseButtons.add(radio);
            groupButtons.add(radio);
        }
        return popupMouseButtons;
    }

    private JPopupMenu getPopupMenuScroll(DropDownButton dropButton) {
        String type = dropButton.getType();
        String action = eventManager.getMouseActions().getAction(type);
        JPopupMenu popupMouseScroll = new JPopupMenu(type);
        popupMouseScroll.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        for (int i = 0; i < actionsScroll.length; i++) {
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(actionsScroll[i].getTitle(), actionsScroll[i].getIcon(), actionsScroll[i]
                    .cmd().equals(action));
            radio.setActionCommand(actionsScroll[i].cmd());
            radio.addActionListener(this);
            popupMouseScroll.add(radio);
            groupButtons.add(radio);
        }

        return popupMouseScroll;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
            if (item.getParent() instanceof JPopupMenu) {
                JPopupMenu pop = (JPopupMenu) item.getParent();
                MouseActions mouseActions = eventManager.getMouseActions();
                mouseActions.setAction(pop.getLabel(), item.getActionCommand());
                ImageViewerPlugin<E> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    view.setMouseActions(mouseActions);
                }
                if (pop.getInvoker() instanceof DropDownButton) {
                    changeButtonState(pop.getLabel(), item.getActionCommand());
                }
            }
        }
    }

    private void displayMeasureToobar() {
        if (!BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.measure.alwaysvisible", false)) {
            if (isCommandActive(ActionW.MEASURE.cmd())) {
                measureToolBar.setVisible(true);
            } else {
                measureToolBar.setVisible(false);
            }
        }
        revalidate();
        repaint();
    }

    public boolean isCommandActive(String cmd) {
        int active = eventManager.getMouseActions().getActiveButtons();
        if (cmd != null
            && cmd.equals(mouseLeft.getActionCommand())
            || (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) && ((mouseMiddle == null)
                ? false : cmd.equals(mouseMiddle.getActionCommand())))
            || (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) && ((mouseRight == null)
                ? false : cmd.equals(mouseRight.getActionCommand())))) {
            return true;
        }
        return false;
    }

    public void changeButtonState(String type, String action) {
        DropDownButton button = getDropDownButton(type);
        if (button != null) {
            Icon icon = buildMouseIcon(type, action);
            button.setIcon(icon);
            button.setActionCommand(action);
            displayMeasureToobar();
        }
    }

    private Icon buildMouseIcon(String type, String action) {
        final Icon mouseIcon = getMouseIcon(type);
        ActionW actionW = getAction(actionsButtons, action);
        final Icon smallIcon = actionW == null ? null : actionW.getIcon();
        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                mouseIcon.paintIcon(c, g, x, y);
                if (smallIcon != null) {
                    x += mouseIcon.getIconWidth() - smallIcon.getIconWidth();
                    y += mouseIcon.getIconHeight() - smallIcon.getIconHeight();
                    smallIcon.paintIcon(c, g, x, y);
                }
            }

            @Override
            public int getIconWidth() {
                return mouseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return mouseIcon.getIconHeight();
            }
        });
    }

    private DropDownButton buildSynchButton() {
        GroupRadioMenu menu = null;
        ActionState synch = eventManager.getAction(ActionW.SYNCH);
        SynchView synchView = SynchView.DEFAULT_STACK;
        if (synch instanceof ComboItemListener) {
            ComboItemListener m = (ComboItemListener) synch;
            Object sel = m.getSelectedItem();
            if (sel instanceof SynchView) {
                synchView = (SynchView) sel;
            }
            menu = new SynchGroupMenu();
            m.registerComponent(menu);
        }
        final DropDownButton button = new DropDownButton(ActionW.SYNCH.cmd(), buildSynchIcon(synchView), menu) { //$NON-NLS-1$
                @Override
                protected JPopupMenu getPopupMenu() {
                    JPopupMenu menu = (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                    menu.setInvoker(this);
                    return menu;
                }

            };
        button.setToolTipText(Messages.getString("ViewerToolBar.synch")); //$NON-NLS-1$
        return button;
    }

    private Icon buildSynchIcon(SynchView synch) {
        final Icon mouseIcon = new ImageIcon(MouseActions.class.getResource("/icon/32x32/synch.png")); //$NON-NLS-1$
        final Icon smallIcon = synch.getIcon();
        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                if (smallIcon != null) {
                    int x2 = x + mouseIcon.getIconWidth() / 2 - smallIcon.getIconWidth() / 2;
                    int y2 = y + mouseIcon.getIconHeight() / 2 - smallIcon.getIconHeight() / 2;
                    smallIcon.paintIcon(c, g, x2, y2);
                }
                mouseIcon.paintIcon(c, g, x, y);
            }

            @Override
            public int getIconWidth() {
                return mouseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return mouseIcon.getIconHeight();
            }
        });
    }

    public DropDownButton getDropDownButton(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return mouseLeft;
        } else if (MouseActions.RIGHT.equals(type)) {
            return mouseRight;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return mouseMiddle;
        } else if (MouseActions.WHEEL.equals(type)) {
            return mouseWheel;
        }
        return null;
    }

    private ActionW getAction(ActionW[] actions, String command) {
        if (actions != null) {
            for (ActionW a : actions) {
                if (a.cmd().equals(command)) {
                    return a;
                }
            }
        }
        return null;
    }

    private Icon getMouseIcon(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return MouseLeftIcon;
        } else if (MouseActions.RIGHT.equals(type)) {
            return MouseRightIcon;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return MouseMiddleIcon;
        } else if (MouseActions.WHEEL.equals(type)) {
            return MouseWheelIcon;
        }
        return MouseLeftIcon;
    }

    public static final ActionW getNextCommand(ActionW[] actions, String command) {
        if (actions != null && actions.length > 0) {
            int index = 0;
            for (int i = 0; i < actions.length; i++) {
                if (actions[i].cmd().equals(command)) {
                    index = i == actions.length - 1 ? 0 : i + 1;
                }
            }
            return actions[index];
        }
        return null;
    }

    class SynchGroupMenu extends GroupRadioMenu {

        public SynchGroupMenu() {
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            super.contentsChanged(e);
            changeButtonState();
        }

        public void changeButtonState() {
            Object sel = dataModel.getSelectedItem();
            if (sel instanceof SynchView && synchButton != null) {
                Icon icon = buildSynchIcon((SynchView) sel);
                synchButton.setIcon(icon);
                synchButton.setActionCommand(sel.toString());
            }
        }
    }
}

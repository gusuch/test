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
package org.weasis.dicom.viewer2d;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;

import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.dockable.DisplayTool;
import org.weasis.dicom.viewer2d.dockable.ImageTool;
import org.weasis.dicom.viewer2d.internal.Activator;

public class View2dContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(View2dContainer.class);

    // TODO read and store models
    public static final GridBagLayoutModel VIEWS_2x1_r1xc2_dump =
        new GridBagLayoutModel(
            View2dContainer.class.getResourceAsStream("/config/layoutModel.xml"), "layout_dump", Messages.getString("View2dContainer.layout_dump"), new ImageIcon( //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                View2dContainer.class.getResource("/icon/22x22/layout1x2_c2.png"))); //$NON-NLS-1$

    // public static final GridBagLayoutModel VIEWS_2x2_mpr = new GridBagLayoutModel(
    // new LinkedHashMap<LayoutConstraints, JComponent>(3), "Orthogonal MPR", null);
    // static {
    // LinkedHashMap<LayoutConstraints, JComponent> constraints = VIEWS_2x2_mpr.getConstraints();
    // constraints.put(new LayoutConstraints(MprView.class.getName(), 0, 0, 0, 1, 2, 0.5, 1.0,
    // GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    // constraints.put(new LayoutConstraints(MprView.class.getName(), 1, 1, 0, 1, 1, 0.5, 0.5,
    // GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    // constraints.put(new LayoutConstraints(MprView.class.getName(), 2, 1, 1, 1, 1, 0.5, 0.5,
    // GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    //
    // }

    public static final GridBagLayoutModel[] MODELS = { VIEWS_1x1, VIEWS_1x2, VIEWS_2x1, VIEWS_2x2_f2, VIEWS_2_f1x2,
        VIEWS_2x1_r1xc2_dump, VIEWS_2x2, VIEWS_3x2, VIEWS_3x3, VIEWS_4x3, VIEWS_4x4 };

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    private static WtoolBar statusBar = null;
    private static boolean INI_COMPONENTS = false;

    public View2dContainer() {
        this(VIEWS_1x1);
    }

    public View2dContainer(GridBagLayoutModel layoutModel) {
        super(EventManager.getInstance(), layoutModel, View2dFactory.NAME, View2dFactory.ICON, null);
        setSynchView(SynchView.DEFAULT_STACK);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;
            // Add standard toolbars
            ViewerToolBar<DicomImageElement> bar = new ViewerToolBar<DicomImageElement>(EventManager.getInstance());
            TOOLBARS.add(0, bar);
            TOOLBARS.add(1, bar.getMeasureToolBar());
            CineToolBar cineBar = new CineToolBar<DicomImageElement>();
            TOOLBARS.add(2, cineBar);

            Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
            if (prefs != null) {
                Preferences prefNode = prefs.node("toolbars"); //$NON-NLS-1$

                boolean cineLoopIsEnabled = prefNode.getBoolean(CineToolBar.class.getName(), false);
                if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.cinetoolbar.alwaysvisible", true)) {
                    cineLoopIsEnabled = true;
                }
                cineBar.setEnabled(cineLoopIsEnabled);
            }

            PluginTool tool = null;

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.dockable.menu.minitools", true)) {
                tool = new MiniTool(Messages.getString("View2dContainer.mini")) { //$NON-NLS-1$

                        @Override
                        public SliderChangeListener[] getActions() {

                            ArrayList<SliderChangeListener> listeners = new ArrayList<SliderChangeListener>(3);
                            ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                            if (seqAction instanceof SliderChangeListener) {
                                listeners.add((SliderChangeListener) seqAction);
                            }
                            ActionState zoomAction = eventManager.getAction(ActionW.ZOOM);
                            if (zoomAction instanceof SliderChangeListener) {
                                listeners.add((SliderChangeListener) zoomAction);
                            }
                            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                            if (rotateAction instanceof SliderChangeListener) {
                                listeners.add((SliderChangeListener) rotateAction);
                            }
                            return listeners.toArray(new SliderChangeListener[listeners.size()]);
                        }
                    };
                // DefaultSingleCDockable dock = tool.registerToolAsDockable();
                // dock.setDefaultLocation(ExtendedMode.NORMALIZED,
                // CLocation.base(UIManager.BASE_AREA).normalRectangle(1.0, 0.0, 0.05, 1.0));
                // dock.setExtendedMode(ExtendedMode.NORMALIZED);
                TOOLS.add(tool);
            }

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.dockable.menu.imagestools", true)) {
                tool = new ImageTool(Messages.getString("View2dContainer.image_tools")); //$NON-NLS-1$
                // tool.registerToolAsDockable();
                TOOLS.add(tool);
            }

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.dockable.menu.display", true)) {
                tool = new DisplayTool(DisplayTool.BUTTON_NAME);
                // tool.registerToolAsDockable();
                TOOLS.add(tool);
                eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
            }

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.dockable.menu.measuretools", true)) {
                tool = new MeasureTool(eventManager);
                // tool.registerToolAsDockable();
                TOOLS.add(tool);
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(DefaultView2d<DicomImageElement> defaultView2d) {
        setSelectedImagePane(defaultView2d);
        if (defaultView2d != null && defaultView2d.getSeries() instanceof DicomSeries) {
            DicomSeries series = (DicomSeries) defaultView2d.getSeries();
            DicomSeries.startPreloading(series, series.copyOfMedias(
                (Filter<DicomImageElement>) defaultView2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                defaultView2d.getCurrentSortComparator()), defaultView2d.getFrameIndex());
        }
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            menuRoot.setText(View2dFactory.NAME);

            List<Action> actions = getPrintActions();
            if (actions != null) {
                JMenu printMenu = new JMenu(Messages.getString("View2dContainer.print")); //$NON-NLS-1$
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
                menuRoot.add(printMenu);
            }

            // ActionState viewingAction = eventManager.getAction(ActionW.VIEWINGPROTOCOL);
            // if (viewingAction instanceof ComboItemListener) {
            // menuRoot.add(((ComboItemListener) viewingAction).createMenu(Messages
            //                    .getString("View2dContainer.view_protocols"))); //$NON-NLS-1$
            // }
            // ActionState presetAction = eventManager.getAction(ActionW.PRESET);
            // if (presetAction instanceof ComboItemListener) {
            // JMenu menu =
            // ((ComboItemListener) presetAction).createUnregisteredRadioMenu(Messages
            //                        .getString("View2dContainer.presets"));//$NON-NLS-1$
            // for (Component mitem : menu.getMenuComponents()) {
            // RadioMenuItem ritem = ((RadioMenuItem) mitem);
            // PresetWindowLevel preset = (PresetWindowLevel) ritem.getObject();
            // if (preset.getKeyCode() > 0) {
            // ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
            // }
            // }
            // menuRoot.add(menu);
            // }
            // ActionState lutShapeAction = eventManager.getAction(ActionW.LUT_SHAPE);
            // if (lutShapeAction instanceof ComboItemListener) {
            // menuRoot.add(((ComboItemListener) lutShapeAction).createMenu("LUT_Shape"));
            // }
            ActionState lutAction = eventManager.getAction(ActionW.LUT);
            if (lutAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) lutAction).createMenu(Messages.getString("View2dContainer.lut")); //$NON-NLS-1$
                ActionState invlutAction = eventManager.getAction(ActionW.INVERSELUT);
                if (invlutAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invlutAction).createMenu(Messages
                        .getString("View2dContainer.inv_lut"))); //$NON-NLS-1$
                }
                menuRoot.add(menu);
            }
            ActionState filterAction = eventManager.getAction(ActionW.FILTER);
            if (filterAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) filterAction).createMenu(Messages.getString("View2dContainer.filter")); //$NON-NLS-1$
                menuRoot.add(menu);
            }
            ActionState stackAction = eventManager.getAction(ActionW.SORTSTACK);
            if (stackAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) stackAction).createMenu(Messages.getString("View2dContainer.sort_stack")); //$NON-NLS-1$
                ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                if (invstackAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invstackAction).createMenu(Messages
                        .getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
                }
                menuRoot.add(menu);
            }
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                menuRoot.add(new JSeparator());
                JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                JMenuItem menuItem = new JMenuItem(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+180")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 180) % 360);
                    }
                });
                menu.add(menuItem);
                ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                if (flipAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) flipAction).createMenu(Messages
                        .getString("View2dContainer.flip_h"))); //$NON-NLS-1$
                    menuRoot.add(menu);
                }
            }

            menuRoot.add(new JSeparator());
            JMenuItem mip = new JMenuItem("Open MIP view"); //$NON-NLS-1$
            mip.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    DefaultView2d<DicomImageElement> selView = getSelectedImagePane();
                    if (selView != null) {
                        MediaSeries<DicomImageElement> s = selView.getSeries();
                        if (s != null && s.size(null) > 2) {
                            setSelectedAndGetFocus();
                            MipView newView2d = new MipView(eventManager);
                            newView2d.registerDefaultListeners();
                            newView2d.setSeries(s);
                            replaceView(selView, newView2d);
                            newView2d.applyMipParameters();
                        }
                    }
                }
            });
            menuRoot.add(mip);

            menuRoot.add(new JSeparator());
            menuRoot.add(ResetTools.createUnregisteredJMenu());

        }
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        super.close();
        View2dFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (DefaultView2d v : view2ds) {
                    v.dispose();
                }
            }
        });

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            if (newVal instanceof SeriesEvent) {
                SeriesEvent event2 = (SeriesEvent) newVal;
                if (ObservableEvent.BasicAction.Add.equals(action)) {
                    SeriesEvent.Action action2 = event2.getActionCommand();
                    Object source = event2.getSource();
                    Object param = event2.getParam();

                    if (SeriesEvent.Action.AddImage.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries series = (DicomSeries) source;
                            DefaultView2d<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
                            DicomImageElement img = view2DPane.getImage();
                            if (img != null && view2DPane.getSeries() == series) {
                                ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                if (seqAction instanceof SliderCineListener) {
                                    SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                    if (param instanceof DicomImageElement) {
                                        Filter<DicomImageElement> filter =
                                            (Filter<DicomImageElement>) view2DPane
                                                .getActionValue(ActionW.FILTERED_SERIES.cmd());
                                        int imgIndex =
                                            series.getImageIndex(img, filter, view2DPane.getCurrentSortComparator());
                                        if (imgIndex < 0) {
                                            imgIndex = 0;
                                            // add again the series for registering listeners
                                            // (require at least one image)
                                            view2DPane.setSeries(series, null);
                                        }
                                        if (imgIndex >= 0) {
                                            sliceAction.setMinMaxValue(1, series.size(filter), imgIndex + 1);
                                        }
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.UpdateImage.equals(action2)) {
                        if (source instanceof DicomImageElement) {
                            DicomImageElement dcm = (DicomImageElement) source;
                            for (DefaultView2d<DicomImageElement> v : view2ds) {
                                if (dcm == v.getImage()) {
                                    // Force to repaint the same image
                                    if (v.getImageLayer().getDisplayImage() == null) {
                                        v.setActionsInView(ActionW.PROGRESSION.cmd(), param);
                                        // Set image to null for getting correct W/L values
                                        v.getImageLayer().setImage(null, null);
                                        v.setSeries(v.getSeries());
                                    } else {
                                        v.propertyChange(new PropertyChangeEvent(dcm, ActionW.PROGRESSION.cmd(), null,
                                            param));
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.loadImageInMemory.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries dcm = (DicomSeries) source;
                            for (DefaultView2d<DicomImageElement> v : view2ds) {
                                if (dcm == v.getSeries()) {
                                    v.repaint(v.getInfoLayer().getPreloadingProgressBound());
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Remove.equals(action)) {
                if (newVal instanceof DicomSeries) {
                    DicomSeries dicomSeries = (DicomSeries) newVal;
                    for (DefaultView2d<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (dicomSeries.equals(s)) {
                            v.setSeries(null);
                        }
                    }
                } else if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                        }
                    }
                    // Study Group
                    else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                        if (event.getSource() instanceof DicomModel) {
                            DicomModel model = (DicomModel) event.getSource();
                            for (MediaSeriesGroup s : model.getChildren(group)) {
                                for (DefaultView2d<DicomImageElement> v : view2ds) {
                                    MediaSeries series = v.getSeries();
                                    if (s.equals(series)) {
                                        v.setSeries(null);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Replace.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (DefaultView2d<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            // Set to null to be sure that all parameters from the view are apply again to the Series
                            // (for instance it is the same series with more images)
                            v.setSeries(null);
                            v.setSeries(series, null);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return View2dFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        // if (MprView.class.getName().equals(classType)) {
        // return new MprView(eventManager, VIEWS_2x2_mpr);
        // }
        return new View2d(eventManager);
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        if (isViewType(DefaultView2d.class, clazz)) {
            return createDefaultView(clazz);
        }

        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            return component;

        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }

        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized WtoolBar getStatusBar() {
        return statusBar;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        List<Action> actions = selectedImagePane == null ? null : selectedImagePane.getExportToClipboardAction();

        if (AbstractProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
            AbstractAction importAll =
                new AbstractAction(
                    Messages.getString("View2dContainer.expOsirixMes"), new ImageIcon(View2dContainer.class.getResource("/icon/16x16/osririx.png"))) { //$NON-NLS-1$//$NON-NLS-2$

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String cmd = "/usr/bin/open -a OsiriX"; //$NON-NLS-1$
                        String baseDir = System.getProperty("weasis.portable.dir"); //$NON-NLS-1$
                        if (baseDir != null) {
                            String prop = System.getProperty("weasis.portable.dicom.directory"); //$NON-NLS-1$
                            if (prop != null) {
                                String[] dirs = prop.split(","); //$NON-NLS-1$
                                File[] files = new File[dirs.length];
                                for (int i = 0; i < files.length; i++) {
                                    File file = new File(baseDir, dirs[i].trim());
                                    if (file.canRead()) {
                                        cmd += " " + file.getAbsolutePath(); //$NON-NLS-1$
                                    }
                                }
                            }
                        } else {
                            File file = new File(AbstractProperties.APP_TEMP_DIR, "dicom"); //$NON-NLS-1$
                            if (file.canRead()) {
                                cmd += " " + file.getAbsolutePath(); //$NON-NLS-1$
                            }
                        }
                        System.out.println("Execute cmd:" + cmd); //$NON-NLS-1$
                        try {
                            Process p = Runtime.getRuntime().exec(cmd);
                            BufferedReader buffer = new BufferedReader(new InputStreamReader(p.getInputStream()));

                            String data;
                            while ((data = buffer.readLine()) != null) {
                                System.out.println(data);
                            }
                            int val = 0;
                            if (p.waitFor() != 0) {
                                val = p.exitValue();
                            }
                            if (val != 0) {
                                JOptionPane.showMessageDialog(View2dContainer.this,
                                    Messages.getString("View2dContainer.expOsirixTitle"), //$NON-NLS-1$
                                    Messages.getString("View2dContainer.expOsirixMes"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                            }

                        } catch (IOException e1) {
                            e1.printStackTrace();
                        } catch (InterruptedException e2) {
                            LOGGER.error("Cannot get the exit status of the open Osirix command: ", e2.getMessage()); //$NON-NLS-1$
                        }
                    }
                };
            if (actions == null) {
                actions = new ArrayList<Action>(1);
            }
            actions.add(importAll);
        }
        return actions;
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<Action>(1);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        AbstractAction printStd =
            new AbstractAction(title, new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png"))) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    Window parent = WinUtil.getParentWindow(View2dContainer.this);
                    PrintDialog dialog = new PrintDialog(parent, title, eventManager);
                    JMVUtils.showCenterScreen(dialog, parent);
                }
            };
        actions.add(printStd);

        final String title2 = Messages.getString("View2dContainer.dcm_print"); //$NON-NLS-1$
        AbstractAction printStd2 = new AbstractAction(title2, null) {

            @Override
            public void actionPerformed(ActionEvent e) {
                Window parent = WinUtil.getParentWindow(View2dContainer.this);
                DicomPrintDialog dialog = new DicomPrintDialog(parent, title2, eventManager);
                JMVUtils.showCenterScreen(dialog, parent);
            }
        };
        actions.add(printStd2);
        return actions;
    }
}

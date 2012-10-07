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
package org.weasis.base.ui.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.TransferHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;
import org.weasis.base.ui.action.ExitAction;
import org.weasis.base.ui.action.OpenPreferencesAction;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.UriListFlavor;
import org.weasis.core.ui.util.WtoolBar.TYPE;

import bibliothek.extension.gui.dock.theme.EclipseTheme;
import bibliothek.extension.gui.dock.theme.eclipse.rex.RexSystemColor;
import bibliothek.gui.DockUI;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.util.ConfiguredBackgroundPanel;
import bibliothek.gui.dock.util.DirectWindowProvider;
import bibliothek.gui.dock.util.Priority;
import bibliothek.gui.dock.util.color.ColorManager;
import bibliothek.gui.dock.util.laf.LookAndFeelColors;
import bibliothek.util.Colors;

public class WeasisWin extends JFrame implements PropertyChangeListener {

    private static final Logger log = LoggerFactory.getLogger(WeasisWin.class);

    private static final JMenu menuFile = new JMenu(Messages.getString("WeasisWin.file")); //$NON-NLS-1$
    private static final JMenu menuDisplay = new JMenu(Messages.getString("WeasisWin.display")); //$NON-NLS-1$
    private static final JMenu menuSelectedPlugin = new JMenu();
    private static ViewerPlugin selectedPlugin = null;
    private static final WeasisWin instance = new WeasisWin();

    private final ToolBarContainer toolbarContainer;

    private volatile boolean busy = false;

    private final List<Runnable> runOnClose = new ArrayList<Runnable>();

    private CFocusListener selectionListener = new CFocusListener() {

        @Override
        public void focusGained(CDockable dockable) {
            if (dockable != null && dockable.getFocusComponent() instanceof ViewerPlugin) {
                setSelectedPlugin((ViewerPlugin) dockable.getFocusComponent());
            }
        }

        @Override
        public void focusLost(CDockable dockable) {
            // TODO Auto-generated method stub

        }
    };

    private WeasisWin() {
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.menu.menubar", true)) {
            this.setJMenuBar(createMenuBar());
        }
        toolbarContainer = new ToolBarContainer();
        this.getContentPane().add(toolbarContainer, BorderLayout.NORTH);
        this.setTitle("Weasis v" + AbstractProperties.WEASIS_VERSION + " " + Messages.getString("WeasisWin.winTitle")); //$NON-NLS-1$
        this.setIconImage(new ImageIcon(UIManager.class.getResource("/icon/logo-button.png")).getImage()); //$NON-NLS-1$
    }

    public static WeasisWin getInstance() {
        return instance;
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            if (!closeWindow()) {
                return;
            }
        }
        super.processWindowEvent(e);
    }

    public boolean closeWindow() {
        if (busy) {
            // TODO add a message, Please wait or kill
            return false;
        }
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.confirm.closing", true)) { //$NON-NLS-1$
            int option = JOptionPane.showConfirmDialog(instance, Messages.getString("WeasisWin.exit_mes")); //$NON-NLS-1$
            if (option == JOptionPane.YES_OPTION) {
                closeAllRunnable();
                System.exit(0);
                return true;
            }
        } else {
            closeAllRunnable();
            System.exit(0);
            return true;
        }
        return false;
    }

    private void closeAllRunnable() {
        for (Runnable onClose : runOnClose) {
            onClose.run();
        }
    }

    public void runOnClose(Runnable run) {
        runOnClose.add(run);
    }

    public void destroyOnClose(final CControl control) {
        runOnClose(new Runnable() {
            @Override
            public void run() {
                control.destroy();
            }
        });
    }

    public void createMainPanel() throws Exception {
        // initToolWindowManager();
        this.setGlassPane(AbstractProperties.glassPane);

        CControl control = UIManager.DOCKING_CONTROL;
        control.setRootWindow(new DirectWindowProvider(this));
        destroyOnClose(control);
        ThemeMap themes = control.getThemes();
        themes.select(ThemeMap.KEY_ECLIPSE_THEME);
        control.getController().getProperties().set(EclipseTheme.PAINT_ICONS_WHEN_DESELECTED, true);
        // control.setGroupBehavior(CGroupBehavior.TOPMOST);
        // control.setDefaultLocation(centerArea.getStationLocation());

        // Fix substance
        LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
        if (laf.getClass().getName().startsWith("org.pushingpixels")) { //$NON-NLS-1$
            ColorManager colors = control.getController().getColors();

            Color selection = javax.swing.UIManager.getColor("TextArea.selectionBackground");
            Color inactiveColor = DockUI.getColor(LookAndFeelColors.TITLE_BACKGROUND).darker();
            Color inactiveColorGradient = DockUI.getColor(LookAndFeelColors.PANEL_BACKGROUND);
            Color activeColor = selection.darker();
            Color ActiveTextColor = javax.swing.UIManager.getColor("TextArea.selectionForeground");

            colors.put(Priority.CLIENT, "stack.tab.border.selected", inactiveColorGradient);
            colors.put(Priority.CLIENT, "stack.tab.border.selected.focused", selection);
            colors.put(Priority.CLIENT, "stack.tab.border.selected.focuslost", inactiveColor);

            colors.put(Priority.CLIENT, "stack.tab.top.selected", inactiveColor);
            colors.put(Priority.CLIENT, "stack.tab.top.selected.focused", activeColor);
            colors.put(Priority.CLIENT, "stack.tab.top.selected.focuslost", inactiveColor);

            colors.put(Priority.CLIENT, "stack.tab.bottom.selected", inactiveColorGradient);
            colors.put(Priority.CLIENT, "stack.tab.bottom.selected.focused", selection);
            colors.put(Priority.CLIENT, "stack.tab.bottom.selected.focuslost", inactiveColor);

            colors.put(Priority.CLIENT, "stack.tab.text.selected", RexSystemColor.getInactiveTextColor());
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focused", ActiveTextColor);
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focuslost", RexSystemColor.getInactiveTextColor());

            colors.put(Priority.CLIENT, "title.flap.active", selection);
            colors.put(Priority.CLIENT, "title.flap.active.text", ActiveTextColor);
            colors.put(Priority.CLIENT, "title.flap.active.knob.highlight", Colors.brighter(selection));
            colors.put(Priority.CLIENT, "title.flap.active.knob.shadow", Colors.darker(selection));
        }

        control.addFocusListener(selectionListener);

        // control.setDefaultLocation(UIManager.BASE_AREA.
        // this.add(UIManager.EAST_AREA, BorderLayout.EAST);
        this.add(UIManager.BASE_AREA, BorderLayout.CENTER);
        UIManager.MAIN_AREA.setLocation(CLocation.base().normalRectangle(0, 0, 1, 1));
        UIManager.MAIN_AREA.setVisible(true);

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Get only ObservableEvent
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object source = event.getNewValue();
            if (evt.getSource() instanceof DataExplorerModel) {
                if (ObservableEvent.BasicAction.Select.equals(action)) {
                    if (source instanceof DataExplorerModel) {
                        DataExplorerModel model = (DataExplorerModel) source;
                        DataExplorerView view = null;
                        synchronized (UIManager.EXPLORER_PLUGINS) {
                            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
                            for (DataExplorerView dataExplorerView : explorers) {
                                if (dataExplorerView.getDataExplorerModel() == model) {
                                    view = dataExplorerView;
                                    break;
                                }
                            }
                            if (view instanceof PluginTool) {
                                PluginTool tool = (PluginTool) view;
                                tool.showDockable();
                            }
                        }
                    }
                    // Select a plugin from that as the same key as the
                    // MediaSeriesGroup
                    else if (source instanceof MediaSeriesGroup) {
                        MediaSeriesGroup group = (MediaSeriesGroup) source;
                        synchronized (UIManager.VIEWER_PLUGINS) {
                            for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                                ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                                if (group.equals(p.getGroupID())) {
                                    p.setSelectedAndGetFocus();
                                    break;
                                }
                            }
                        }
                    }
                } else if (ObservableEvent.BasicAction.Register.equals(action)) {
                    if (source instanceof ViewerPlugin) {
                        registerPlugin((ViewerPlugin) source);
                    } else if (source instanceof ViewerPluginBuilder) {
                        ViewerPluginBuilder builder = (ViewerPluginBuilder) source;
                        SeriesViewerFactory factory = builder.getFactory();
                        DataExplorerModel model = builder.getModel();
                        List<MediaSeries> series = builder.getSeries();

                        if (series != null && builder.isCompareEntryToBuildNewViewer()
                            && model.getTreeModelNodeForNewPlugin() != null && model instanceof TreeModel) {
                            TreeModel treeModel = (TreeModel) model;
                            if (series.size() == 1) {
                                MediaSeries s = series.get(0);
                                MediaSeriesGroup group = treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
                                openSeriesInViewerPlugin(factory, model, group, series, builder.isRemoveOldSeries(),
                                    builder.getScreenBound());
                            } else if (series.size() > 1) {
                                HashMap<MediaSeriesGroup, List<MediaSeries>> map =
                                    getSeriesByEntry(treeModel, series, model.getTreeModelNodeForNewPlugin());
                                for (Iterator<Entry<MediaSeriesGroup, List<MediaSeries>>> iterator =
                                    map.entrySet().iterator(); iterator.hasNext();) {
                                    Entry<MediaSeriesGroup, List<MediaSeries>> entry = iterator.next();
                                    MediaSeriesGroup group = entry.getKey();
                                    List<MediaSeries> seriesList = entry.getValue();
                                    openSeriesInViewerPlugin(factory, model, group, seriesList,
                                        builder.isRemoveOldSeries(), builder.getScreenBound());
                                }
                            }

                        } else {
                            openSeriesInViewerPlugin(factory, model, null, series, true, builder.getScreenBound());

                        }

                    }
                } else if (ObservableEvent.BasicAction.Unregister.equals(action)) {
                    if (source instanceof SeriesViewerFactory) {
                        SeriesViewerFactory viewerFactory = (SeriesViewerFactory) source;
                        final List<ViewerPlugin> pluginsToRemove = new ArrayList<ViewerPlugin>();
                        String name = viewerFactory.getUIName();
                        synchronized (UIManager.VIEWER_PLUGINS) {
                            for (final ViewerPlugin plugin : UIManager.VIEWER_PLUGINS) {
                                if (name.equals(plugin.getName())) {
                                    // Do not close Series directly, it can produce deadlock.
                                    pluginsToRemove.add(plugin);
                                }
                            }
                        }
                        UIManager.closeSeriesViewer(pluginsToRemove);
                    }
                }
            } else if (event.getSource() instanceof ViewerPlugin) {
                ViewerPlugin plugin = (ViewerPlugin) event.getSource();
                if (ObservableEvent.BasicAction.UpdateToolbars.equals(action)) {
                    updateToolbars(selectedPlugin == null ? null : selectedPlugin.getToolBar(), plugin.getToolBar(),
                        true);
                }
            }
        }
    }

    private HashMap<MediaSeriesGroup, List<MediaSeries>> getSeriesByEntry(TreeModel treeModel,
        List<MediaSeries> series, TreeModelNode entry) {
        HashMap<MediaSeriesGroup, List<MediaSeries>> map = new HashMap<MediaSeriesGroup, List<MediaSeries>>();
        if (series != null && treeModel != null && entry != null) {
            for (MediaSeries s : series) {
                MediaSeriesGroup entry1 = treeModel.getParent(s, entry);
                List<MediaSeries> seriesList = map.get(entry1);
                if (seriesList == null) {
                    seriesList = new ArrayList<MediaSeries>();
                }
                seriesList.add(s);
                map.put(entry1, seriesList);
            }
        }
        return map;
    }

    private void openSeriesInViewerPlugin(SeriesViewerFactory factory, DataExplorerModel model, MediaSeriesGroup group,
        List<MediaSeries> seriesList, boolean removeOldSeries, Rectangle screenBound) {
        if (factory == null || seriesList == null || seriesList.size() == 0) {
            return;
        }
        if (screenBound == null && factory != null && group != null) {
            synchronized (UIManager.VIEWER_PLUGINS) {
                for (final ViewerPlugin p : UIManager.VIEWER_PLUGINS) {
                    if (p instanceof ImageViewerPlugin && p.getName().equals(factory.getUIName())
                        && group.equals(p.getGroupID())) {

                        ImageViewerPlugin viewer = ((ImageViewerPlugin) p);
                        viewer.addSeriesList(seriesList, removeOldSeries);
                        return;
                    }
                }
            }
        }
        // Pass the DataExplorerModel to the viewer
        Hashtable<String, Object> properties = new Hashtable<String, Object>();
        properties.put(DataExplorerModel.class.getName(), model);
        if (seriesList.size() > 1) {
            properties.put(DefaultView2d.class.getName(), seriesList.size());
        }
        SeriesViewer seriesViewer = factory.createSeriesViewer(properties);
        if (seriesViewer instanceof MimeSystemAppViewer) {
            for (MediaSeries m : seriesList) {
                seriesViewer.addSeries(m);
            }
        } else if (seriesViewer instanceof ViewerPlugin) {
            ViewerPlugin viewer = (ViewerPlugin) seriesViewer;
            if (group != null) {
                viewer.setGroupID(group);
                String title = group.toString();
                if (title.length() > 30) {
                    viewer.setToolTipText(title);
                    title = title.substring(0, 30);
                    title = title.concat("..."); //$NON-NLS-1$
                }
                viewer.setPluginName(title);
            }
            boolean isregistered;
            if (screenBound != null) {
                isregistered = registerDetachWindow(viewer, screenBound);
            } else {
                isregistered = registerPlugin(viewer);
            }
            if (isregistered) {
                viewer.setSelectedAndGetFocus();
                if (seriesViewer instanceof ImageViewerPlugin) {
                    ((ImageViewerPlugin) viewer).selectLayoutPositionForAddingSeries(seriesList);
                }
                for (MediaSeries m : seriesList) {
                    viewer.addSeries(m);
                }
                viewer.setSelected(true);
            } else {
                viewer.close();
            }
        }
    }

    private boolean registerDetachWindow(final ViewerPlugin plugin, Rectangle screenBound) {
        if (screenBound != null) {
            ViewerPlugin oldWin = null;
            synchronized (UIManager.VIEWER_PLUGINS) {
                Dialog old = null;
                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                    ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                    if (p.getDockable().isExternalizable()) {
                        Dialog dialog = WinUtil.getParentDialog(p);
                        old = dialog;
                        if (dialog != null && old != dialog
                            && screenBound.equals(WinUtil.getClosedScreenBound(dialog.getBounds()))) {
                            oldWin = p;
                            break;
                        }
                    }
                }
            }

            final DefaultSingleCDockable dock = plugin.getDockable();
            dock.setExternalizable(true);

            if (oldWin == null) {
                dock.setLocation(CLocation.external(screenBound.x, screenBound.y, screenBound.width - 150,
                    screenBound.height - 150));
                plugin.showDockable();
                GuiExecutor.instance().execute(new Runnable() {

                    @Override
                    public void run() {
                        if (dock.isVisible()) {
                            dock.setExtendedMode(ExtendedMode.MAXIMIZED);
                        }
                    }
                });
            } else {
                Component parent = WinUtil.getParentOfClass(oldWin, ConfiguredBackgroundPanel.class);
                if (parent == null) {
                    return false;
                } else {
                    Rectangle b2 = parent.getBounds();
                    b2.setLocation(parent.getLocationOnScreen());
                    dock.setLocation(CLocation.external(b2.x, b2.y, b2.width, b2.height).stack());
                    plugin.showDockable();
                }
            }
            return true;
        }
        return false;
    }

    public boolean registerPlugin(final ViewerPlugin plugin) {
        if (plugin == null || UIManager.VIEWER_PLUGINS.contains(plugin)) {
            return false;
        }
        plugin.showDockable();
        return true;
    }

    public synchronized ViewerPlugin getSelectedPlugin() {
        return selectedPlugin;
    }

    public synchronized void setSelectedPlugin(ViewerPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (selectedPlugin == plugin) {
            plugin.requestFocusInWindow();
            return;
        }
        ViewerPlugin oldPlugin = selectedPlugin;
        selectedPlugin = plugin;
        selectedPlugin.setSelected(true);
        selectedPlugin.fillSelectedPluginMenu(menuSelectedPlugin);

        List<DockableTool> tool = selectedPlugin.getToolPanel();
        List<DockableTool> oldTool = oldPlugin == null ? null : oldPlugin.getToolPanel();

        if (tool == null) {
            if (oldTool != null) {
                for (DockableTool p : oldTool) {
                    p.closeDockable();
                }
            }
        } else {
            if (tool != oldTool) {
                if (oldTool != null) {
                    for (DockableTool p : oldTool) {
                        p.closeDockable();
                    }
                }
                for (int i = 0; i < tool.size(); i++) {
                    DockableTool p = tool.get(i);
                    p.showDockable();
                }
            }
        }

        List<Toolbar> toolBar = selectedPlugin.getToolBar();
        List<Toolbar> oldToolBar = oldPlugin == null ? null : oldPlugin.getToolBar();

        updateToolbars(oldToolBar, toolBar, false);

    }

    private void updateToolbars(List<Toolbar> oldToolBar, List<Toolbar> toolBar, boolean force) {
        if (toolBar == null) {
            if (oldToolBar != null) {
                toolbarContainer.unregisterAll();
            }
            toolbarContainer.registerToolBar(ToolBarContainer.EMPTY);
            toolbarContainer.revalidate();
            toolbarContainer.repaint();
        } else {
            if (force || toolBar != oldToolBar) {
                if (oldToolBar != null) {
                    toolbarContainer.unregisterAll();
                }
                for (Toolbar t : toolBar) {
                    toolbarContainer.registerToolBar(t);
                }
                toolbarContainer.revalidate();
                toolbarContainer.repaint();
            }
        }
    }

    @Override
    public GhostGlassPane getGlassPane() {
        return AbstractProperties.glassPane;
    }

    public void showWindow() throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Toolkit kit = Toolkit.getDefaultToolkit();
        // Rectangle maxBound = ge.getMaximumWindowBounds();
        // TODO command line maximize screen: 0 => all screens, 1,2 => first,
        // second screen or 1-2 for two screens, or 2-4
        // three screens from the second one
        // int minScreen = 0;
        // int maxScreen = 0;
        Rectangle bound = null;
        // Get size of each screen
        // GraphicsDevice[] gs = ge.getScreenDevices();
        // minScreen = minScreen < gs.length ? minScreen : gs.length - 1;
        // maxScreen = maxScreen < minScreen ? minScreen : maxScreen < gs.length ? maxScreen : gs.length - 1;
        // for (int j = minScreen; j <= maxScreen; j++) {
        // GraphicsConfiguration config = gs[j].getDefaultConfiguration();
        // Rectangle b = config.getBounds();
        // Insets inset = kit.getScreenInsets(config);
        // b.x -= inset.left;
        // b.y -= inset.top;
        // b.width -= inset.right;
        // b.height -= inset.bottom;
        // if (bound == null) {
        // bound = b;
        // } else {
        // bound = bound.union(b);
        // }
        // }
        GraphicsConfiguration config = ge.getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle b = config.getBounds();
        Insets inset = kit.getScreenInsets(config);
        b.x -= inset.left;
        b.y -= inset.top;
        b.width -= inset.right;
        b.height -= inset.bottom;
        bound = b;

        log.debug("Max main screen bound: {}", bound.toString()); //$NON-NLS-1$
        setMaximizedBounds(bound);
        // set a valid size, insets of screen is often non consistent
        setBounds(bound.x, bound.y, bound.width - 150, bound.height - 150);
        setVisible(true);

        setExtendedState((getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH ? JFrame.NORMAL
            : JFrame.MAXIMIZED_BOTH);
        log.info("End of loading the GUI..."); //$NON-NLS-1$

    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        buildMenuFile();
        menuBar.add(menuFile);
        buildMenuDisplay();
        menuBar.add(menuDisplay);
        menuBar.add(menuSelectedPlugin);
        final JMenu helpMenuItem = new JMenu(Messages.getString("WeasisWin.help")); //$NON-NLS-1$
        final String helpURL = System.getProperty("weasis.help.url"); //$NON-NLS-1$
        if (helpURL != null) {
            final JMenuItem helpContentMenuItem = new JMenuItem(Messages.getString("WeasisWin.guide")); //$NON-NLS-1$
            helpContentMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        JMVUtils.OpenInDefaultBrowser(helpContentMenuItem, new URL(helpURL));
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                }
            });
            helpMenuItem.add(helpContentMenuItem);
        }

        final JMenuItem webMenuItem = new JMenuItem(Messages.getString("WeasisWin.shortcuts")); //$NON-NLS-1$
        webMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    URL url = new URL(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.shortcuts")); //$NON-NLS-1$
                    JMVUtils.OpenInDefaultBrowser(webMenuItem, url);
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
        helpMenuItem.add(webMenuItem);
        final JMenuItem websiteMenuItem = new JMenuItem(Messages.getString("WeasisWin.online")); //$NON-NLS-1$
        websiteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    URL url = new URL(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")); //$NON-NLS-1$
                    JMVUtils.OpenInDefaultBrowser(websiteMenuItem, url);
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
        helpMenuItem.add(websiteMenuItem);
        final JMenuItem aboutMenuItem = new JMenuItem(Messages.getString("WeasisAboutBox.title")); //$NON-NLS-1$
        aboutMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                WeasisAboutBox about = new WeasisAboutBox();
                JMVUtils.showCenterScreen(about, instance);
            }
        });
        helpMenuItem.add(aboutMenuItem);
        menuBar.add(helpMenuItem);
        return menuBar;
    }

    private void buildToolBarSubMenu(final JMenu toolBarMenu) {
        List<Toolbar> bars = toolbarContainer.getRegisteredToolBars();
        for (final Toolbar bar : bars) {
            if (!TYPE.main.equals(bar.getType()) && !TYPE.conditional.equals(bar.getType())) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(bar.getBarName(), bar.getComponent().isEnabled());
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (e.getSource() instanceof JCheckBoxMenuItem) {
                            bar.getComponent().setEnabled(((JCheckBoxMenuItem) e.getSource()).isSelected());
                            toolbarContainer.showToolbar(bar.getComponent());
                        }
                    }
                });
                toolBarMenu.add(item);
            }
        }
    }

    // private void buildToolSubMenu(final JMenu toolMenu) {
    // List<DockableTool> tools = selectedPlugin == null ? null : selectedPlugin.getToolPanel();
    // if (tools != null) {
    // for (DockableTool t : tools) {
    // if (t instanceof PluginTool && PluginTool.TYPE.tool.equals(((PluginTool) t).getType())) {
    // toolMenu.add(((PlaceholderDockable) t.registerToolAsDockable()).createMenuItem());
    // }
    // }
    // }
    // }

    private static void buildPrintSubMenu(final JMenu printMenu) {
        if (selectedPlugin != null) {
            List<Action> actions = selectedPlugin.getPrintActions();
            if (actions != null) {
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
            }
        }
    }

    private static void buildOpenSubMenu(final JMenu importMenu) {
        synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
            List<SeriesViewerFactory> viewers = UIManager.SERIES_VIEWER_FACTORIES;
            for (final SeriesViewerFactory view : viewers) {
                List<Action> actions = view.getOpenActions();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        importMenu.add(item);
                    }
                }
            }
        }
    }

    private static void buildImportSubMenu(final JMenu importMenu) {
        synchronized (UIManager.EXPLORER_PLUGINS) {
            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
            for (final DataExplorerView dataExplorerView : explorers) {
                List<Action> actions = dataExplorerView.getOpenImportDialogAction();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        importMenu.add(item);
                    }
                }
            }
        }
    }

    private static void buildExportSubMenu(final JMenu exportMenu) {
        // TODO export workspace in as preference

        //                final AbstractAction saveAction = new AbstractAction("Save workspace layout") { //$NON-NLS-1$
        //
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // // Handle workspace ui persistence
        // PersistenceDelegate pstDelegate = UIManager.toolWindowManager.getPersistenceDelegate();
        // try {
        //                                pstDelegate.save(new FileOutputStream(new File("/home/nicolas/Documents/test.xml"))); //$NON-NLS-1$
        // } catch (FileNotFoundException e1) {
        // e1.printStackTrace();
        // }
        // }
        // };
        // exportMenu.add(saveAction);

        synchronized (UIManager.EXPLORER_PLUGINS) {
            if (selectedPlugin != null) {
                List<Action> actions = selectedPlugin.getExportActions();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        exportMenu.add(item);
                    }
                }
            }

            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
            for (final DataExplorerView dataExplorerView : explorers) {
                List<Action> actions = dataExplorerView.getOpenExportDialogAction();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        exportMenu.add(item);
                    }
                }
            }
        }
    }

    private void buildMenuDisplay() {
        menuDisplay.removeAll();

        DynamicMenu toolBarMenu = new DynamicMenu(Messages.getString("WeasisWin.toolbar")) {//$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildToolBarSubMenu(this);

                }
            };
        toolBarMenu.addPopupMenuListener();
        menuDisplay.add(toolBarMenu);

        // final JMenu toolMenu = new JMenu("Tools");
        // JPopupMenu menuTool = toolMenu.getPopupMenu();
        // // #WEA-6 - workaround, PopupMenuListener doesn't work on Mac in the top bar with native look and feel
        // if (AbstractProperties.isMacNativeLookAndFeel()) {
        // toolMenu.addChangeListener(new ChangeListener() {
        // @Override
        // public void stateChanged(ChangeEvent e) {
        // if (toolMenu.isSelected()) {
        // buildToolSubMenu(toolMenu);
        // } else {
        // toolMenu.removeAll();
        // }
        // }
        // });
        // } else {
        // menuTool.addPopupMenuListener(new PopupMenuListener() {
        //
        // @Override
        // public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        // buildToolSubMenu(toolMenu);
        // }
        //
        // @Override
        // public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        // toolMenu.removeAll();
        // }
        //
        // @Override
        // public void popupMenuCanceled(PopupMenuEvent e) {
        // }
        // });
        // }
        // menuDisplay.add(toolMenu);
    }

    private static void buildMenuFile() {
        menuFile.removeAll();
        DynamicMenu openMenu = new DynamicMenu(Messages.getString("WeasisWin.open")) { //$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildOpenSubMenu(this);
                }
            };
        openMenu.addPopupMenuListener();
        menuFile.add(openMenu);

        DynamicMenu importMenu = new DynamicMenu(Messages.getString("WeasisWin.import")) {//$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildImportSubMenu(this);
                }
            };
        importMenu.addPopupMenuListener();
        menuFile.add(importMenu);

        DynamicMenu exportMenu = new DynamicMenu(Messages.getString("WeasisWin.export")) {//$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildExportSubMenu(this);
                }
            };
        exportMenu.addPopupMenuListener();

        menuFile.add(exportMenu);
        menuFile.add(new JSeparator());
        DynamicMenu printMenu = new DynamicMenu("Print") {//$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildPrintSubMenu(this);
                }
            };
        printMenu.addPopupMenuListener();
        menuFile.add(printMenu);
        menuFile.add(new JSeparator());
        menuFile.add(new JMenuItem(OpenPreferencesAction.getInstance()));
        menuFile.add(new JSeparator());
        menuFile.add(new JMenuItem(ExitAction.getInstance()));
    }

    private class SequenceHandler extends TransferHandler {

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
        }

        @Override
        public Transferable createTransferable(JComponent comp) {
            if (comp instanceof Thumbnail) {
                MediaSeries t = ((Thumbnail) comp).getSeries();
                if (t instanceof Series) {
                    return t;
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            if (support.isDataFlavorSupported(Series.sequenceDataFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable transferable = support.getTransferable();

            List<File> files = null;
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropFiles(files, null);
            }
            // When dragging a file or group of files from a Gnome or Kde environment
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.uriListFlavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropFiles(files, null);
            }

            Series seq;
            try {
                seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
                if (seq != null) {
                    synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                        for (final SeriesViewerFactory factory : UIManager.SERIES_VIEWER_FACTORIES) {
                            if (factory.canReadMimeType(seq.getMimeType())) {
                                DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
                                if (model instanceof TreeModel) {
                                    TreeModel treeModel = (TreeModel) model;
                                    MediaSeriesGroup group =
                                        treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());
                                    ArrayList<MediaSeries> list = new ArrayList<MediaSeries>(1);
                                    list.add(seq);
                                    openSeriesInViewerPlugin(factory, model, group, list, true, null);
                                }
                                break;
                            }
                        }
                    }

                }
            } catch (Exception e) {
                return false;
            }

            return true;

        }

        private boolean dropFiles(List<File> files, DataExplorerView explorer) {
            // TODO get the current explorer
            if (files != null) {
                // LoadLocalDicom dicom = new LoadLocalDicom(files.toArray(new File[files.size()]), true, model);
                // DicomModel.loadingExecutor.execute(dicom);
                return true;
            }
            return false;
        }
    }
}

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
package org.weasis.core.ui.internal;

import java.io.File;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class Activator implements BundleActivator, ServiceListener {

    private static final String pluginViewerFilter = String.format(
        "(%s=%s)", Constants.OBJECTCLASS, SeriesViewerFactory.class.getName()); //$NON-NLS-1$
    public static final BundlePreferences PREFERENCES = new BundlePreferences();

    private static BundleContext bundleContext = null;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        Activator.bundleContext = bundleContext;
        PREFERENCES.init(bundleContext);
        MeasureTool.viewSetting.applyPreferences(PREFERENCES.getDefaultPreferences());

        bundleContext.addServiceListener(this, pluginViewerFilter);
        // must be instantiate in the EDT
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                ServiceTracker m_tracker = new ServiceTracker(bundleContext, SeriesViewerFactory.class.getName(), null);
                m_tracker.open();
                Object[] services = m_tracker.getServices();
                for (int i = 0; (services != null) && (i < services.length); i++) {
                    if (!UIManager.SERIES_VIEWER_FACTORIES.contains(services[i])
                        && services[i] instanceof SeriesViewerFactory) {
                        UIManager.SERIES_VIEWER_FACTORIES.add((SeriesViewerFactory) services[i]);
                        // Activator.log(LogService.LOG_INFO, "Register viewer Plug-in: " + m_ref.toString());
                    }
                }
                // closing tracker will uregister services
                // m_tracker.close();
            }
        });
        File dataFolder = AbstractProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            FileUtil.readProperties(new File(dataFolder, "persitence.properties"), BundleTools.LOCAL_PERSISTENCE);//$NON-NLS-1$
        }
    }

    // @Override
    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        MeasureTool.viewSetting.savePreferences(PREFERENCES.getDefaultPreferences());
        PREFERENCES.close();
        File dataFolder = AbstractProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            File file = new File(dataFolder, "persitence.properties"); //$NON-NLS-1$
            FileUtil.prepareToWriteFile(file);
            FileUtil.storeProperties(file, BundleTools.LOCAL_PERSISTENCE, null);//$NON-NLS-1$
        }
        Activator.bundleContext = null;
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {

        // must be instantiate in the EDT
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (bundleContext != null) {
                    ServiceReference m_ref = event.getServiceReference();
                    SeriesViewerFactory viewerFactory = (SeriesViewerFactory) bundleContext.getService(m_ref);
                    if (viewerFactory == null) {
                        return;
                    }

                    // TODO manage when several identical MimeType, register the default one
                    if (event.getType() == ServiceEvent.REGISTERED) {
                        if (!UIManager.SERIES_VIEWER_FACTORIES.contains(viewerFactory)) {
                            UIManager.SERIES_VIEWER_FACTORIES.add(viewerFactory);
                            // Activator.log(LogService.LOG_INFO, "Register viewer Plug-in: " + m_ref.toString());
                        }
                    } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                        if (UIManager.SERIES_VIEWER_FACTORIES.contains(viewerFactory)) {
                            // Activator.log(LogService.LOG_INFO, "Unregister viewer Plug-in: " + m_ref.toString());
                            UIManager.SERIES_VIEWER_FACTORIES.remove(viewerFactory);
                            // Unget service object and null references.
                            bundleContext.ungetService(m_ref);
                        }
                    }
                }
            }
        });
    }

    public static BundleContext getBundleContext() {
        return bundleContext;
    }

}

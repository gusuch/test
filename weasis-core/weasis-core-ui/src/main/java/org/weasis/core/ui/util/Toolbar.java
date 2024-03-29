/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.Dimension;

import org.weasis.core.ui.util.WtoolBar.TYPE;

public interface Toolbar {

    public static final Dimension SEPARATOR_2x24 = new Dimension(2, 24);

    TYPE getType();

    String getBarName();

    WtoolBar getComponent();
}

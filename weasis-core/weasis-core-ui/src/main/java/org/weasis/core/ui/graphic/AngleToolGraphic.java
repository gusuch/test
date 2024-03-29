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
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit,Benoit Jacquemoud
 */
public class AngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AngleToolGraphic.class.getResource("/icon/22x22/draw-angle.png")); //$NON-NLS-1$

    public static final Measurement ANGLE = new Measurement(Messages.getString("measure.angle"), 1, true); //$NON-NLS-1$
    public static final Measurement COMPLEMENTARY_ANGLE = new Measurement(
        Messages.getString("measure.complement_angle"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement REFLEX_ANGLE = new Measurement(Messages.getString("AngleToolGraphic.reflex_angle"), 3, true, true, false); //$NON-NLS-1$

    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    Point2D ptA, ptO, ptB; // Let AOB be the triangle that represents the measured angle, O being the intersection point

    boolean lineColinear; // estimate if OA & OB line segments are colinear not not
    boolean lineOAvalid, lineOBvalid; // estimate if line segments are valid or not

    double angleDeg; // smallest angle in Degrees in the range of [-180 ; 180] between OA & OB line segments

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AngleToolGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(3, paintColor, lineThickness, labelVisible);
        init();
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("measure.angle"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        if (lineOAvalid) {
            path.append(new Line2D.Double(ptA, ptO), false);
        }

        if (lineOBvalid) {
            path.append(new Line2D.Double(ptO, ptB), false);
        }

        if (lineOAvalid && lineOBvalid && !lineColinear) {
            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(2));
            aShape.addShape(path);

            // Let arcAngle be the partial section of the ellipse that represents the measured angle
            double startingAngle = GeomUtil.getAngleDeg(ptO, ptA);

            double radius = 32;
            Rectangle2D arcAngleBounds =
                new Rectangle2D.Double(ptO.getX() - radius, ptO.getY() - radius, 2 * radius, 2 * radius);

            Shape arcAngle = new Arc2D.Double(arcAngleBounds, startingAngle, angleDeg, Arc2D.OPEN);

            double rMax = Math.min(ptO.distance(ptA), ptO.distance(ptB)) * 2 / 3;
            double scalingMin = radius / rMax;

            aShape.addInvShape(arcAngle, ptO, scalingMin, true);

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {

        if (layer != null && layer.getSourceImage() != null && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getSourceImage().getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                double positiveAngle = Math.abs(angleDeg);

                if (ANGLE.isComputed()) {
                    measVal.add(new MeasureItem(ANGLE, positiveAngle, Messages.getString("measure.deg"))); //$NON-NLS-1$
                }

                if (COMPLEMENTARY_ANGLE.isComputed()) {
                    measVal.add(new MeasureItem(COMPLEMENTARY_ANGLE, 180.0 - positiveAngle, Messages
                        .getString("measure.deg"))); //$NON-NLS-1$
                }
                if (REFLEX_ANGLE.isComputed()) {
                    measVal
                        .add(new MeasureItem(REFLEX_ANGLE, 360.0 - positiveAngle, Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    public boolean isShapeValid() {
        updateTool();
        return lineOAvalid && lineOBvalid;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void init() {
        ptA = getHandlePoint(0);
        ptO = getHandlePoint(1);
        ptB = getHandlePoint(2);

        lineColinear = false;
        lineOAvalid = lineOBvalid = false;

        angleDeg = 0.0;
    }

    protected void updateTool() {
        init();

        lineOAvalid = (ptA != null && ptO != null && !ptO.equals(ptA));
        lineOBvalid = (ptB != null && ptO != null && !ptO.equals(ptB));

        if (lineOAvalid && lineOBvalid) {
            angleDeg = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(ptA, ptO, ptB));
            lineColinear = GeomUtil.lineColinear(ptO, ptA, ptO, ptB);
        }
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(ANGLE);
        list.add(COMPLEMENTARY_ANGLE);
        list.add(REFLEX_ANGLE);
        return list;
    }
}

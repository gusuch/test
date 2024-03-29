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
package org.weasis.core.ui.graphic.model;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractLayer.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractLayer implements Comparable, Serializable, Layer {

    private static final long serialVersionUID = -6113490831569841167L;

    protected final PropertyChangeListener pcl;
    protected final transient ArrayList<LayerModel> canvas = new ArrayList<LayerModel>();
    private boolean masked;
    private int level;
    private final int drawType;
    protected volatile GraphicList graphics;

    /**
     * The Class PropertyChangeHandler.
     * 
     * @author Nicolas Roduit
     */
    class PropertyChangeHandler implements PropertyChangeListener, Serializable {

        // This method gets called when a bound property is changed, inherite by PropertyChangeListener
        @Override
        public void propertyChange(PropertyChangeEvent propertychangeevent) {
            Object obj = propertychangeevent.getSource();
            String s = propertychangeevent.getPropertyName();
            if (obj instanceof Graphic) {
                Graphic graph = (Graphic) obj;
                if ("bounds".equals(s)) { //$NON-NLS-1$
                    graphicBoundsChanged(graph, (Shape) propertychangeevent.getOldValue(),
                        (Shape) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("graphicLabel".equals(s)) { //$NON-NLS-1$
                    labelBoundsChanged(graph, (GraphicLabel) propertychangeevent.getOldValue(),
                        (GraphicLabel) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("remove".equals(s)) { //$NON-NLS-1$
                    removeGraphic(graph);
                } else if ("remove.repaint".equals(s)) { //$NON-NLS-1$
                    removeGraphicAndRepaint(graph);
                } else if ("toFront".equals(s)) { //$NON-NLS-1$
                    toFront(graph);
                } else if ("toBack".equals(s)) { //$NON-NLS-1$
                    toBack(graph);
                }
            }

        }

        private static final long serialVersionUID = -9094820911680205527L;

        private PropertyChangeHandler() {
        }
    }

    public AbstractLayer(LayerModel canvas1, int drawMode) {
        this.drawType = drawMode;
        level = drawMode;
        this.canvas.add(canvas1);
        graphics = new GraphicList();
        pcl = new PropertyChangeHandler();
    }

    public void addGraphic(Graphic graphic) {
        if (graphics != null && !graphics.contains(graphic)) {
            graphics.add(graphic);
            graphic.setLayerID(drawType);
            graphic.addPropertyChangeListener(pcl);
            ArrayList<AbstractLayer> layers = graphics.getLayers();
            if (layers != null) {
                for (AbstractLayer layer : layers) {
                    graphic.addPropertyChangeListener(layer.pcl);
                    layer.repaint(graphic.getRepaintBounds(getAffineTransform()));
                }
            }
        }
    }

    public void toFront(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
            graphics.add(graphic);
            repaint(graphic.getRepaintBounds(getAffineTransform()));
        }
    }

    public synchronized void setGraphics(GraphicList graphics) {
        if (this.graphics != graphics) {
            if (this.graphics != null) {
                this.graphics.removeLayer(this);
                for (Graphic graphic : this.graphics) {
                    graphic.removePropertyChangeListener(pcl);
                }
                getShowDrawing().setSelectedGraphics(null);
            }
            if (graphics == null) {
                this.graphics = new GraphicList();
            } else {
                this.graphics = graphics;
                this.graphics.addLayer(this);
                for (Graphic graphic : this.graphics) {
                    graphic.addPropertyChangeListener(pcl);
                }
            }
        }
    }

    public void toBack(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
            graphics.add(0, graphic);
            repaint(graphic.getRepaintBounds(getAffineTransform()));
        }
    }

    public void setShowDrawing(LayerModel canvas1) {
        if (canvas != null) {
            if (!canvas.contains(canvas1)) {
                this.canvas.add(canvas1);
            }
        }
    }

    public LayerModel getShowDrawing() {
        return canvas.get(0);
    }

    @Override
    public void setVisible(boolean flag) {
        this.masked = !flag;
    }

    @Override
    public boolean isVisible() {
        return !masked;
    }

    @Override
    public void setLevel(int i) {
        level = i;
    }

    @Override
    public int getLevel() {
        return level;
    }

    // private AffineTransform getAffineTransform() {
    protected AffineTransform getAffineTransform() {
        LayerModel layerModel = getShowDrawing();
        if (layerModel != null) {
            GraphicsPane graphicsPane = layerModel.getGraphicsPane();
            if (graphicsPane != null) {
                return graphicsPane.getAffineTransform();
            }
        }
        return null;
    }

    public void removeGraphicAndRepaint(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        repaint(graphic.getTransformedBounds(graphic.getShape(), getAffineTransform()));

        if (graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public void removeGraphic(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        if (graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public java.util.List<Graphic> getGraphics() {
        return graphics;
    }

    public abstract List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform);

    public abstract List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform,
        boolean onlyFrontGraphic);

    public abstract List<Graphic> getGraphicsBoundsInArea(Rectangle rect);

    public abstract Graphic getGraphicContainPoint(MouseEventDouble mouseevent);

    public abstract List<Graphic> getGraphicListContainPoint(MouseEventDouble mouseevent);

    public abstract void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform,
        Rectangle2D bound);

    // public abstract void paintSVG(SVGGraphics2D g2);

    // interface comparable, permet de trier par ordre croissant les layers
    @Override
    public int compareTo(Object obj) {
        int thisVal = this.getLevel();
        int anotherVal = ((AbstractLayer) obj).getLevel();
        return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
    }

    public void repaint(Rectangle rectangle) {
        if (rectangle != null) {
            for (int i = 0; i < canvas.size(); i++) {
                canvas.get(i).repaint(rectangle);
            }
        }
    }

    public void repaint() {
        for (int i = 0; i < canvas.size(); i++) {
            canvas.get(i).repaint();
        }
    }

    protected Rectangle rectangleUnion(Rectangle rectangle, Rectangle rectangle1) {
        if (rectangle == null) {
            return rectangle1;
        }
        return rectangle1 == null ? rectangle : rectangle.union(rectangle1);
    }

    protected void graphicBoundsChanged(Graphic graphic, Shape oldShape, Shape shape, AffineTransform transform) {
        if (graphic != null) {
            if (oldShape == null) {
                if (shape != null) {
                    Rectangle rect = graphic.getTransformedBounds(shape, transform);
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            } else {
                if (shape == null) {
                    Rectangle rect = graphic.getTransformedBounds(oldShape, transform);
                    if (rect != null) {
                        repaint(rect);
                    }
                } else {
                    Rectangle rect =
                        rectangleUnion(graphic.getTransformedBounds(oldShape, transform),
                            graphic.getTransformedBounds(shape, transform));
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            }
        }
    }

    protected void labelBoundsChanged(Graphic graphic, GraphicLabel oldLabel, GraphicLabel newLabel,
        AffineTransform transform) {

        if (graphic != null) {
            if (oldLabel == null) {
                if (newLabel != null) {
                    Rectangle2D rect = graphic.getTransformedBounds(newLabel, transform);
                    GeomUtil.growRectangle(rect, 2);
                    if (rect != null) {
                        repaint(rect.getBounds());
                    }
                }
            } else {
                if (newLabel == null) {
                    Rectangle2D rect = graphic.getTransformedBounds(oldLabel, transform);
                    GeomUtil.growRectangle(rect, 2);
                    if (rect != null) {
                        repaint(rect.getBounds());
                    }
                } else {
                    Rectangle2D newRect = graphic.getTransformedBounds(newLabel, transform);
                    GeomUtil.growRectangle(newRect, 2);

                    Rectangle2D oldRect = graphic.getTransformedBounds(oldLabel, transform);
                    GeomUtil.growRectangle(oldRect, 2);

                    Rectangle rect = rectangleUnion(oldRect.getBounds(), newRect.getBounds());
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            }
        }
    }

    public int getDrawType() {
        return drawType;
    }

    public void deleteAllGraphic() {
        if (graphics != null) {
            if (graphics.getLayerSize() >= 0) {
                setGraphics(null);
            } else {
                for (int i = graphics.size() - 1; i >= 0; i--) {
                    removeGraphic(graphics.get(i));
                }
            }
            repaint();
        }
    }

}

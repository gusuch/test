package org.weasis.dicom.viewer2d.mpr;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TransposeDescriptor;
import javax.vecmath.Vector3d;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.TagUtils;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.mpr.MprView.Type;

public class SeriesBuilder {
    public static final File MPR_CACHE_DIR = new File(AbstractProperties.APP_TEMP_DIR, "mpr"); //$NON-NLS-1$
    static {
        try {
            MPR_CACHE_DIR.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createMissingSeries(MPRContainer mprContainer, MprView view) throws Exception {
        // TODO test images have all the same size and pixel spacing
        MediaSeries<DicomImageElement> series = view.getSeries();
        if (series != null) {
            SeriesComparator sort = (SeriesComparator) view.getActionValue(ActionW.SORTSTACK.cmd());
            // Get the reverse to write coronal and sagittal images from the head to the feet
            Comparator sortFilter = sort.getReversOrderComparator();
            Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
            DicomImageElement img = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, filter, sortFilter);
            if (img != null) {
                double[] or = (double[]) img.getTagValue(TagW.ImageOrientationPatient);
                if (or != null && or.length == 6) {
                    double[] pos = (double[]) img.getTagValue(TagW.ImagePositionPatient);
                    if (pos != null && pos.length == 3) {
                        HashMap<TagW, Object> tags = img.getMediaReader().getMediaFragmentTags(0);
                        if (tags != null) {
                            DicomObject dcmObj = ((DicomMediaIO) img.getMediaReader()).getDicomObject();
                            // clean tags
                            removeAllPrivateTags(dcmObj);

                            int size = series.size(filter);
                            Iterable<DicomImageElement> medias = series.getMedias(filter, sortFilter);
                            int width = (Integer) img.getTagValue(TagW.Columns);
                            int height = (Integer) img.getTagValue(TagW.Rows);

                            double origPixSize = img.getPixelSize();
                            Float stackPixSize = (Float) img.getTagValue(TagW.SliceThickness);
                            double sPixSize = stackPixSize == null ? origPixSize : stackPixSize;
                            if (origPixSize * width < sPixSize * size) {
                                // origPixSize = origPixSize / sPixSize;
                                sPixSize = 1.0 / sPixSize;
                            }

                            String seriesID = (String) series.getTagValue(TagW.SubseriesInstanceUID);

                            RawImage[] secSeries = new RawImage[height];
                            writeBlock(secSeries, medias, false);

                            // tags.put(TagW.SliceThickness, new Float(origPixSize));
                            // tags.put(TagW.PixelSpacing, new double[] { sPixSize, origPixSize });
                            dcmObj.putString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);

                            // dcmObj.putInt(Tag.Columns, VR.US, width);
                            dcmObj.putInt(Tag.Rows, VR.US, size);
                            dcmObj.putDouble(Tag.SliceThickness, VR.DS, origPixSize);
                            dcmObj.putDoubles(Tag.PixelSpacing, VR.DS, new double[] { sPixSize, origPixSize });
                            dcmObj.putString(Tag.SeriesInstanceUID, VR.UI, "m2." + seriesID);

                            Vector3d vr = new Vector3d(or[0], or[1], or[2]);
                            Vector3d vc = new Vector3d(or[3], or[4], or[5]);

                            Vector3d resc = new Vector3d(0.0, 0.0, 0.0);
                            resc = new Vector3d(0.0, 0.0, 0.0);
                            rotate(vc, vr, -Math.toRadians(90), resc);
                            dcmObj.putDoubles(Tag.ImageOrientationPatient, VR.DS, new double[] { or[0], or[1], or[2],
                                resc.x, resc.y, resc.z });

                            DicomSeries dicomSeries = new DicomSeries("m2." + seriesID);
                            buildDicomSeries(secSeries, dicomSeries, dcmObj, false, origPixSize, pos);
                            MprView coronal = mprContainer.getMprView(Type.CORONAL);
                            if (coronal != null) {
                                coronal.setSeries(dicomSeries);
                                coronal.repaint();
                            }

                            // Build Third Series
                            RawImage[] thirdSeries = new RawImage[width];
                            writeBlock(thirdSeries, medias, true);

                            dcmObj.putInt(Tag.Columns, VR.US, height);
                            dcmObj.putInt(Tag.Rows, VR.US, size);
                            // dcmObj.putDouble(Tag.SliceThickness, VR.DS, origPixSize);
                            // dcmObj.putDoubles(Tag.PixelSpacing, VR.DS, new double[] { sPixSize, origPixSize });
                            dcmObj.putString(Tag.SeriesInstanceUID, VR.UI, "m3." + seriesID);

                            // Compute column axis
                            Vector3d norm = new Vector3d(ImageOrientation.computeNormalVectorOfPlan(or));
                            // resc = new Vector3d(0.0, 0.0, 0.0);
                            // rotate(vc, norm, -Math.toRadians(270), resc);

                            // Compute row axis
                            Vector3d resr = new Vector3d(0.0, 0.0, 0.0);
                            // rotate(vr, norm, -Math.toRadians(270), resr);
                            rotate(vr, vc, Math.toRadians(90), resr);
                            dcmObj.putDoubles(Tag.ImageOrientationPatient, VR.DS, new double[] { resr.x, resr.y,
                                resr.z, or[3], or[4], or[5] });
                            DicomSeries dicomSeries2 = new DicomSeries("m3." + seriesID);
                            buildDicomSeries(thirdSeries, dicomSeries2, dcmObj, true, origPixSize, pos);
                            MprView sagittal = mprContainer.getMprView(Type.SAGITTAL);
                            if (sagittal != null) {
                                sagittal.setSeries(dicomSeries2);
                                sagittal.repaint();
                            }
                        }

                    }
                }
            }
        }
    }

    private static void buildDicomSeries(RawImage[] newSeries, DicomSeries dicomSeries, DicomObject dcmObj,
        boolean rotate, double origPixSize, double[] pos) throws Exception {
        String prefix = rotate ? "m3." : "m2.";
        int last = newSeries.length - 1;
        for (int i = 0; i < newSeries.length; i++) {

            byte[] bytesOut = getBytesFromFile(newSeries[i].getFile());
            if (bytesOut == null) {
                throw new IllegalAccessException("Cannot read raw image!");
            }
            int index = i;
            dcmObj.putString(Tag.SOPInstanceUID, VR.UI, prefix + (index + 1));
            dcmObj.putInt(Tag.InstanceNumber, VR.IS, index + 1);
            double location = pos[1] + index * origPixSize;
            dcmObj.putDouble(Tag.SliceLocation, VR.DS, location);
            dcmObj.putDoubles(Tag.ImagePositionPatient, VR.DS, new double[] { rotate ? location : pos[0],
                rotate ? pos[1] : location, pos[2] });

            dcmObj.putBytes(Tag.PixelData, VR.OW, bytesOut);

            writeDICOM(newSeries[i], dcmObj);

            newSeries[i].getFile().delete();

        }
        for (int i = 0; i < newSeries.length; i++) {
            File inFile = newSeries[i].getFile();
            String name = FileUtil.nameWithoutExtension(inFile.getName());
            File file = new File(MPR_CACHE_DIR, name + ".dcm");
            if (file.canRead()) {
                DicomMediaIO dicomReader = new DicomMediaIO(file);
                if (dicomReader.readMediaTags()) {
                    try {
                        if (i == 0) {
                            dicomReader.writeMetaData(dicomSeries);
                        }

                        MediaElement[] medias = dicomReader.getMediaElement();
                        if (medias != null) {
                            for (MediaElement media : medias) {
                                dicomSeries.add((DicomImageElement) media);
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        dicomReader.reset();
                    }
                }
            }
        }
    }

    private static void writeBlock(RawImage[] newSeries, Iterable<DicomImageElement> medias, boolean rotate)
        throws IOException {
        try {
            for (int i = 0; i < newSeries.length; i++) {
                newSeries[i] = new RawImage();
            }
            synchronized (medias) {
                Iterator<DicomImageElement> iter = medias.iterator();
                while (iter.hasNext()) {
                    DicomImageElement dcm = iter.next();
                    // TODO do not open more than 512 files (Limitation to open 1024 in the same
                    // time on Ubuntu)
                    PlanarImage image = dcm.getImage();
                    if (image == null) {
                        throw new IOException("an image cannot be read!");
                    }
                    writeRasterInRaw(rotate ? getRotateImage(image) : image.getAsBufferedImage(), newSeries);
                }
            }
        } finally {
            for (int i = 0; i < newSeries.length; i++) {
                if (newSeries[i] != null) {
                    newSeries[i].disposeOutputStream();
                }
            }
        }
    }

    private static void writeRasterInRaw(BufferedImage image, RawImage[] newSeries) throws IOException {
        if (newSeries != null && image != null && image.getHeight() == newSeries.length) {

            DataBuffer dataBuffer = image.getRaster().getDataBuffer();
            int width = image.getWidth();
            int height = newSeries.length;
            byte[] bytesOut = null;
            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
                for (int j = 0; j < height; j++) {
                    newSeries[j].getOutputStream().write(bytesOut, j * width, width);
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data =
                    dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();
                bytesOut = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 2] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                }
                width *= 2;
                for (int j = 0; j < height; j++) {
                    newSeries[j].getOutputStream().write(bytesOut, j * width, width);
                }
            }

        }
    }

    private static BufferedImage getRotateImage(RenderedImage source) {
        RenderedOp result;
        // use Transpose operation
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(source);
        pb.add(TransposeDescriptor.ROTATE_270);
        result = JAI.create("transpose", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        // Handle non square images. Translation is necessary because the transpose operator keeps the same
        // origin (top left not the center of the image)
        float diffw = source.getWidth() / 2.0f - result.getWidth() / 2.0f;
        float diffh = source.getHeight() / 2.0f - result.getHeight() / 2.0f;
        if (diffw != 0.0f || diffh != 0.0f) {
            pb = new ParameterBlock();
            pb.addSource(result);
            pb.add(diffw);
            pb.add(diffh);
            result = JAI.create("translate", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        }
        return result.getAsBufferedImage();
    }

    private static void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x =
            axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.x
                * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y =
            axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.y
                * Math.cos(angle) + (axis.z * vSrc.x + axis.x * vSrc.z) * Math.sin(angle);
        vDst.z =
            axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.z
                * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
    }

    private static void removeAllPrivateTags(DicomObject dcmObj) {
        Iterator it = dcmObj.datasetIterator();
        while (it.hasNext()) {
            DicomElement element = (DicomElement) it.next();
            if (TagUtils.isPrivateDataElement(element.tag())) {
                dcmObj.remove(element.tag());
            }
        }
    }

    private static boolean writeDICOM(RawImage newSeries, DicomObject dcmObj) throws Exception {
        DicomOutputStream out = null;
        DicomInputStream dis = null;
        File inFile = newSeries.getFile();
        String name = FileUtil.nameWithoutExtension(inFile.getName());
        File outFile = new File(MPR_CACHE_DIR, name + ".dcm");

        try {
            out = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            out.writeDicomFile(dcmObj);
        } catch (IOException e) {
            //     LOGGER.warn("", e); //$NON-NLS-1$
            outFile.delete();
            return false;
        } finally {
            FileUtil.safeClose(out);
            FileUtil.safeClose(dis);
        }
        return true;
    }

    public static byte[] getBytesFromFile(File file) {
        FileInputStream is = null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            is = new FileInputStream(file);
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(is);
        }
        return null;
    }

    static class RawImage {
        private File file;
        private FileOutputStream outputStream;

        public RawImage() throws IOException {
            file = File.createTempFile("mpr_", ".raw", MPR_CACHE_DIR);//$NON-NLS-1$ //$NON-NLS-2$
        }

        public File getFile() {
            return file;
        }

        public FileOutputStream getOutputStream() throws FileNotFoundException {
            if (outputStream == null) {
                outputStream = new FileOutputStream(file);
            }
            return outputStream;
        }

        public void disposeOutputStream() {
            if (outputStream != null) {
                FileUtil.safeClose(outputStream);
                outputStream = null;
            }
        }
    }
}

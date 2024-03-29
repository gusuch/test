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
package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.NullDescriptor;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.imageio.ImageReaderFactory;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.iod.module.pr.DisplayShutterModule;
import org.dcm4che2.util.ByteUtils;
import org.dcm4che2.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends DicomImageReader implements MediaReader<PlanarImage> {

    /**
     * Logger for this class
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaIO.class);

    public static final String MIMETYPE = "application/dicom"; //$NON-NLS-1$
    public static final String IMAGE_MIMETYPE = "image/dicom"; //$NON-NLS-1$
    public static final String SERIES_VIDEO_MIMETYPE = "video/dicom"; //$NON-NLS-1$
    public static final String SERIES_MIMETYPE = "series/dicom"; //$NON-NLS-1$
    public static final String SERIES_PR_MIMETYPE = "pr/dicom"; //$NON-NLS-1$
    public static final String SERIES_KO_MIMETYPE = "ko/dicom"; //$NON-NLS-1$
    public static final String SERIES_SR_MIMETYPE = "sr/dicom"; //$NON-NLS-1$
    public static final String SERIES_ENCAP_DOC_MIMETYPE = "encap/dicom"; //$NON-NLS-1$
    public static final String SERIES_XDSI = "xds-i/dicom"; //$NON-NLS-1$
    public static final String NO_VALUE = org.weasis.dicom.codec.Messages.getString("DicomMediaIO.unknown");//$NON-NLS-1$

    private URI uri;
    private DicomObject dicomObject = null;
    private int numberOfFrame;
    private int bitsStored;
    private final HashMap<TagW, Object> tags;
    private MediaElement[] image = null;
    private ImageInputStream imageStream = null;
    private volatile String mimeType;

    private ImageReader jpipReader;

    public DicomMediaIO(URI uri) {
        super(DicomCodec.DicomImageReaderSpi);
        this.uri = uri;
        numberOfFrame = 0;
        this.tags = new HashMap<TagW, Object>();
        mimeType = MIMETYPE;
    }

    public DicomMediaIO(File source) {
        this(source.toURI());
    }

    public DicomMediaIO(URL url) throws URISyntaxException {
        this(url.toURI());
    }

    @Override
    public synchronized void replaceURI(URI uri) {
        if (uri != null && !uri.equals(this.uri)) {
            this.uri = uri;
            reset();
        }

    }

    public boolean readMediaTags() {
        if (dicomObject == null && uri != null) {

            try {
                if (uri.toString().startsWith("file:/")) { //$NON-NLS-1$
                    imageStream = ImageIO.createImageInputStream(new File(uri));
                } else {
                    // TODO test if url stream is closed on reset !
                    imageStream = ImageIO.createImageInputStream(uri.toURL().openStream());
                }

                setInput(imageStream, false, false);
                if (getStreamMetadata() instanceof DicomStreamMetaData) {
                    dicomObject = ((DicomStreamMetaData) getStreamMetadata()).getDicomObject();
                }
                // Exclude DICOMDIR
                if (dicomObject == null
                    || dicomObject.getString(Tag.MediaStorageSOPClassUID, "").equals("1.2.840.10008.1.3.10")) { //$NON-NLS-1$ //$NON-NLS-2$
                    close();
                    return false;
                }

                bitsStored = dicomObject.getInt(Tag.BitsStored, dicomObject.getInt(Tag.BitsAllocated, 0));
                if (bitsStored > 0) {
                    numberOfFrame = getNumImages(false);
                    String tsuid = dicomObject.getString(Tag.TransferSyntaxUID, ""); //$NON-NLS-1$
                    if (tsuid.startsWith("1.2.840.10008.1.2.4.10")) { //$NON-NLS-1$
                        // MPEG2 MP@ML 1.2.840.10008.1.2.4.100
                        // MEPG2 MP@HL 1.2.840.10008.1.2.4.101
                        // MPEG4 AVC/H.264 1.2.840.10008.1.2.4.102
                        // MPEG4 AVC/H.264 BD 1.2.840.10008.1.2.4.103
                        mimeType = SERIES_VIDEO_MIMETYPE;
                    } else {
                        mimeType = IMAGE_MIMETYPE;
                    }
                } else {
                    boolean special = setDicomSpecialType(dicomObject);
                    if (!special) {
                        close();
                        return false;
                    }
                }

                writeInstanceTags();

            } catch (Throwable t) {
                LOGGER.warn("", t); //$NON-NLS-1$
                close();
                return false;
            }
            // writeInstanceTags();
        }
        return true;
    }

    @Override
    protected void initImageReader(int imageIndex) throws IOException {
        super.initImageReader(imageIndex);
        // TODO 1.2.840.10008.1.2.4.95 (DICOM JPIP Referenced Deflate Transfer Syntax)
        if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
            setTagNoNull(TagW.PixelDataProviderURL, dicomObject.getString(Tag.PixelDataProviderURL));
            MediaElement[] elements = getMediaElement();
            // TODO handle frame
            if (elements != null && elements.length > 0) {
                reader.setInput(elements[0]);
            }
        }
    }

    private boolean setDicomSpecialType(DicomObject dicom) {
        String modality = dicom.getString(Tag.Modality);
        if (modality != null) {
            if ("PR".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_PR_MIMETYPE;
                return true;
            } else if ("KO".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_KO_MIMETYPE;
                return true;
            } else if ("SR".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_SR_MIMETYPE;
                return true;
            } else {
                String encap = dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument);
                if (encap != null) {
                    mimeType = SERIES_ENCAP_DOC_MIMETYPE;
                    return true;
                }
            }
        }
        return false;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setTag(TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public void setTagNoNull(TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    public static void setTag(Map<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public static void setTagNoNull(Map<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tags.get(tag);
    }

    private void writeTag(MediaSeriesGroup group, TagW tag) {
        group.setTag(tag, getTagValue(tag));
    }

    public void writeMetaData(MediaSeriesGroup group) {
        if (group == null) {
            return;
        }
        // Get the dicom header
        DicomObject header = getDicomObject();
        writeMetaData(group, header);

        // Series Group
        if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // Information for series ToolTips
            group.setTagNoNull(TagW.PatientName, getTagValue(TagW.PatientName));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));

            if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
                MediaElement[] elements = getMediaElement();
                if (elements != null) {
                    for (MediaElement m : elements) {
                        m.setTag(TagW.ExplorerModel, group.getTagValue(TagW.ExplorerModel));
                    }
                }
            }
        }
    }

    public static void writeMetaData(MediaSeriesGroup group, DicomObject header) {
        if (group == null || header == null) {
            return;
        }
        // Patient Group
        if (TagW.PatientPseudoUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            group.setTag(TagW.PatientID, header.getString(Tag.PatientID, NO_VALUE));
            group.setTag(TagW.PatientName, buildPatientName(header.getString(Tag.PatientName)));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.PatientBirthDate, getDateFromDicomElement(header, Tag.PatientBirthDate, null));
            group.setTagNoNull(TagW.PatientBirthTime, getDateFromDicomElement(header, Tag.PatientBirthTime, null));
            // Sex attribute can have the following values: M(male), F(female), or O(other)
            String val = header.getString(Tag.PatientSex, "O"); //$NON-NLS-1$
            group
                .setTag(
                    TagW.PatientSex,
                    val.startsWith("F") ? Messages.getString("DicomMediaIO.female") : val.startsWith("M") ? Messages.getString("DicomMediaIO.Male") : Messages.getString("DicomMediaIO.other")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            group.setTagNoNull(TagW.IssuerOfPatientID, header.getString(Tag.IssuerOfPatientID));
            group.setTagNoNull(TagW.PatientWeight, getFloatFromDicomElement(header, Tag.PatientWeight, null));
            group.setTagNoNull(TagW.PatientComments, header.getString(Tag.PatientComments));
        }
        // Study Group
        else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // StudyInstanceUID is the unique identifying tag for this study group
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.StudyID, header.getString(Tag.StudyID));
            group.setTagNoNull(TagW.StudyTime, getDateFromDicomElement(header, Tag.StudyTime, null));
            // Merge date and time, used in display
            group.setTagNoNull(
                TagW.StudyDate,
                TagW.dateTime(getDateFromDicomElement(header, Tag.StudyDate, null),
                    (Date) group.getTagValue(TagW.StudyTime)));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));
            group.setTagNoNull(TagW.StudyComments, header.getString(Tag.StudyComments));

            group.setTagNoNull(TagW.AccessionNumber, header.getString(Tag.AccessionNumber));
            group.setTagNoNull(TagW.ModalitiesInStudy, header.getString(Tag.ModalitiesInStudy));
            group.setTagNoNull(TagW.NumberOfStudyRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedInstances, null));
            group.setTagNoNull(TagW.NumberOfStudyRelatedSeries,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedSeries, null));

            group.setTagNoNull(TagW.StudyStatusID, header.getString(Tag.StudyStatusID));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ProcedureCodeSequence, header.get(Tag.ProcedureCodeSequence));
        }
        // Series Group
        else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // SubseriesInstanceUID is the unique identifying tag for this series group
            group.setTag(TagW.SeriesInstanceUID, header.getString(Tag.SeriesInstanceUID, NO_VALUE));
            group.setTag(TagW.Modality, header.getString(Tag.Modality, NO_VALUE));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.SeriesDate,
                header.getDate(Tag.SeriesDate, getDateFromDicomElement(header, Tag.StudyDate, null)));

            group.setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
            group.setTagNoNull(TagW.RetrieveAETitle, header.getString(Tag.RetrieveAETitle));
            group.setTagNoNull(TagW.ReferringPhysicianName, header.getString(Tag.ReferringPhysicianName));
            group.setTagNoNull(TagW.InstitutionName, header.getString(Tag.InstitutionName));
            group.setTagNoNull(TagW.InstitutionalDepartmentName, header.getString(Tag.InstitutionalDepartmentName));
            group.setTagNoNull(TagW.StationName, header.getString(Tag.StationName));
            group.setTagNoNull(TagW.Manufacturer, header.getString(Tag.Manufacturer));
            group.setTagNoNull(TagW.ManufacturerModelName, header.getString(Tag.ManufacturerModelName));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ReferencedPerformedProcedureStepSequence,
                header.get(Tag.ReferencedPerformedProcedureStepSequence));
            group.setTagNoNull(TagW.SeriesNumber, getIntegerFromDicomElement(header, Tag.SeriesNumber, null));
            group.setTagNoNull(TagW.PreferredPlaybackSequencing,
                getIntegerFromDicomElement(header, Tag.PreferredPlaybackSequencing, null));
            group.setTagNoNull(
                TagW.CineRate,
                getIntegerFromDicomElement(header, Tag.CineRate,
                    getIntegerFromDicomElement(header, Tag.RecommendedDisplayFrameRate, null)));
            group.setTagNoNull(TagW.KVP, getFloatFromDicomElement(header, Tag.KVP, null));
            group.setTagNoNull(TagW.Laterality, header.getString(Tag.Laterality));
            group.setTagNoNull(TagW.BodyPartExamined, header.getString(Tag.BodyPartExamined));
            group.setTagNoNull(TagW.ReferencedImageSequence, header.get(Tag.ReferencedImageSequence));
            group.setTagNoNull(TagW.FrameOfReferenceUID, header.getString(Tag.FrameOfReferenceUID));
            group.setTagNoNull(TagW.NumberOfSeriesRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfSeriesRelatedInstances, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartDate,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartDate, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartTime,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartTime, null));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.RequestAttributesSequence, header.get(Tag.RequestAttributesSequence));

        }
    }

    private void writeInstanceTags() {

        if (dicomObject != null && tags.size() == 0) {
            // -------- Mandatory Tags --------
            // Tags for identifying group (Patient, Study, Series)

            String patientID = dicomObject.getString(Tag.PatientID, NO_VALUE);
            setTag(TagW.PatientID, patientID);
            String name = buildPatientName(dicomObject.getString(Tag.PatientName));
            setTag(TagW.PatientName, name);
            Date birthdate = getDateFromDicomElement(dicomObject, Tag.PatientBirthDate, null);
            setTagNoNull(TagW.PatientBirthDate, birthdate);
            // Global Identifier for the patient.
            setTag(TagW.PatientPseudoUID,
                buildPatientPseudoUID(patientID, dicomObject.getString(Tag.IssuerOfPatientID), name, birthdate));
            setTag(TagW.StudyInstanceUID, dicomObject.getString(Tag.StudyInstanceUID, NO_VALUE));
            setTag(TagW.SeriesInstanceUID, dicomObject.getString(Tag.SeriesInstanceUID, NO_VALUE));
            setTag(TagW.Modality, dicomObject.getString(Tag.Modality, NO_VALUE));
            setTag(TagW.InstanceNumber, dicomObject.getInt(Tag.InstanceNumber, TagW.AppID.incrementAndGet()));
            setTag(TagW.SOPInstanceUID,
                dicomObject.getString(Tag.SOPInstanceUID, getTagValue(TagW.InstanceNumber).toString()));
            // -------- End of Mandatory Tags --------

            writeOnlyinstance(dicomObject);
            writeSharedFunctionalGroupsSequence(dicomObject);
            writePerFrameFunctionalGroupsSequence(tags, dicomObject, 0);
            if (SERIES_PR_MIMETYPE.equals(mimeType)) {
                // Set the series list for applying the PR
                setTagNoNull(TagW.ReferencedSeriesSequence, dicomObject.get(Tag.ReferencedSeriesSequence));
                // Set the name of the PR
                setTagNoNull(TagW.SeriesDescription, dicomObject.getString(Tag.SeriesDescription));
            } else if (SERIES_KO_MIMETYPE.equals(mimeType)) {
                // Set the series list for applying the PR
                setTagNoNull(TagW.CurrentRequestedProcedureEvidenceSequence,
                    dicomObject.get(Tag.CurrentRequestedProcedureEvidenceSequence));
                // Set the name of the PR
                setTagNoNull(TagW.SeriesDescription, dicomObject.getString(Tag.SeriesDescription));
            }
            validateDicomImageValues(tags);
            computeSlicePositionVector(tags);

            Area shape = buildShutterArea(dicomObject);
            if (shape != null) {
                setTagNoNull(TagW.ShutterFinalShape, shape);
                Integer psVal = getIntegerFromDicomElement(dicomObject, Tag.ShutterPresentationValue, null);
                setTagNoNull(TagW.ShutterPSValue, psVal);
                float[] rgb =
                    DisplayShutterModule.convertToFloatLab(dicomObject.getInts(Tag.ShutterPresentationColorCIELabValue,
                        (int[]) null));
                Color color =
                    rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb,
                        (int[]) null);
                setTagNoNull(TagW.ShutterRGBColor, color);

            }
            computeSUVFactor(dicomObject, tags, 0);
        }
    }

    public static String buildPatientName(String rawName) {
        String name = rawName == null ? NO_VALUE : rawName;
        if (name.trim().equals("")) { //$NON-NLS-1$
            name = NO_VALUE;
        }
        return name.replace("^", " "); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static String buildPatientPseudoUID(String patientID, String issuerOfPatientID, String patientName,
        Date birthdate) {
        // Build a global identifier for the patient.
        StringBuffer buffer = new StringBuffer(patientID == null ? NO_VALUE : patientID);
        if (issuerOfPatientID != null && !"".equals(issuerOfPatientID.trim())) { //$NON-NLS-1$
            // patientID + issuerOfPatientID => should be unique globally
            buffer.append(issuerOfPatientID);
        } else {
            // Try to make it unique.
            if (birthdate != null) {
                buffer.append(TagW.dicomformatDate.format(birthdate).toString());
            }
            if (patientName != null) {
                buffer.append(patientName.substring(0, patientName.length() < 5 ? patientName.length() : 5));
            }
        }
        return buffer.toString();

    }

    private void writeSharedFunctionalGroupsSequence(DicomObject dicomObject) {

        if (dicomObject != null) {
            DicomElement seq = dicomObject.get(Tag.SharedFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(0);
                } catch (Exception e) {
                    LOGGER.warn("", e); //$NON-NLS-1$
                }
                if (dcm != null) {
                    writeFunctionalGroupsSequence(tags, dcm);
                }
            }
        }
    }

    private void writeOnlyinstance(DicomObject dicomObject) {
        if (dicomObject != null && tags != null) {
            boolean signed = dicomObject.getInt(Tag.PixelRepresentation) != 0;
            // Instance tags
            setTagNoNull(TagW.ImageType, getStringFromDicomElement(dicomObject, Tag.ImageType, null));
            setTagNoNull(TagW.ImageComments, dicomObject.getString(Tag.ImageComments));
            setTagNoNull(TagW.ImageLaterality,
                dicomObject.getString(Tag.ImageLaterality, dicomObject.getString(Tag.Laterality)));
            setTagNoNull(TagW.ContrastBolusAgent, dicomObject.getString(Tag.ContrastBolusAgent));
            setTagNoNull(TagW.TransferSyntaxUID, dicomObject.getString(Tag.TransferSyntaxUID));
            setTagNoNull(TagW.SOPClassUID, dicomObject.getString(Tag.SOPClassUID));
            setTagNoNull(TagW.ScanningSequence, dicomObject.getString(Tag.ScanningSequence));
            setTagNoNull(TagW.SequenceVariant, dicomObject.getString(Tag.SequenceVariant));
            setTagNoNull(TagW.ScanOptions, dicomObject.getString(Tag.ScanOptions));
            setTagNoNull(TagW.RepetitionTime, getFloatFromDicomElement(dicomObject, Tag.RepetitionTime, null));
            setTagNoNull(TagW.EchoTime, getFloatFromDicomElement(dicomObject, Tag.EchoTime, null));
            setTagNoNull(TagW.InversionTime, getFloatFromDicomElement(dicomObject, Tag.InversionTime, null));
            setTagNoNull(TagW.EchoNumbers, getIntegerFromDicomElement(dicomObject, Tag.EchoNumbers, null));
            setTagNoNull(TagW.GantryDetectorTilt, getFloatFromDicomElement(dicomObject, Tag.GantryDetectorTilt, null));
            setTagNoNull(TagW.ConvolutionKernel, dicomObject.getString(Tag.ConvolutionKernel));
            setTagNoNull(TagW.FlipAngle, getFloatFromDicomElement(dicomObject, Tag.FlipAngle, null));
            setTagNoNull(TagW.PatientOrientation, dicomObject.getStrings(Tag.PatientOrientation, (String[]) null));
            setTagNoNull(TagW.SliceLocation, getFloatFromDicomElement(dicomObject, Tag.SliceLocation, null));
            setTagNoNull(TagW.SliceThickness, getFloatFromDicomElement(dicomObject, Tag.SliceThickness, null));
            setTagNoNull(TagW.AcquisitionDate, getDateFromDicomElement(dicomObject, Tag.AcquisitionDate, null));
            setTagNoNull(TagW.AcquisitionTime, getDateFromDicomElement(dicomObject, Tag.AcquisitionTime, null));
            setTagNoNull(TagW.ContentTime, getDateFromDicomElement(dicomObject, Tag.ContentTime, null));

            setTagNoNull(TagW.ImagePositionPatient, dicomObject.getDoubles(Tag.ImagePositionPatient, (double[]) null));
            setTagNoNull(TagW.ImageOrientationPatient,
                dicomObject.getDoubles(Tag.ImageOrientationPatient, (double[]) null));
            setTagNoNull(
                TagW.ImageOrientationPlane,
                ImageOrientation
                    .makeImageOrientationLabelFromImageOrientationPatient((double[]) getTagValue(TagW.ImageOrientationPatient)));

            setTagNoNull(TagW.ImagerPixelSpacing, dicomObject.getDoubles(Tag.ImagerPixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelSpacing, dicomObject.getDoubles(Tag.PixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelAspectRatio, dicomObject.getInts(Tag.PixelAspectRatio, (int[]) null));
            setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                dicomObject.getString(Tag.PixelSpacingCalibrationDescription));

            setTagNoNull(TagW.ModalityLUTSequence, dicomObject.get(Tag.ModalityLUTSequence));
            setTagNoNull(TagW.RescaleSlope, getFloatFromDicomElement(dicomObject, Tag.RescaleSlope, null));
            setTagNoNull(TagW.RescaleIntercept, getFloatFromDicomElement(dicomObject, Tag.RescaleIntercept, null));
            setTagNoNull(TagW.RescaleType, getStringFromDicomElement(dicomObject, Tag.RescaleType, null));

            setTagNoNull(TagW.VOILUTSequence, dicomObject.get(Tag.VOILUTSequence));
            setTagNoNull(TagW.WindowWidth, getFloatArrayFromDicomElement(dicomObject, Tag.WindowWidth, null));
            setTagNoNull(TagW.WindowCenter, getFloatArrayFromDicomElement(dicomObject, Tag.WindowCenter, null));
            setTagNoNull(TagW.WindowCenterWidthExplanation,
                getStringArrayFromDicomElement(dicomObject, Tag.WindowCenterWidthExplanation, null));
            setTagNoNull(TagW.VOILutFunction, getStringFromDicomElement(dicomObject, Tag.VOILUTFunction, null));

            setTagNoNull(TagW.Units, dicomObject.getString(Tag.Units));

            setTagNoNull(TagW.SmallestImagePixelValue,
                getIntPixelValue(dicomObject, Tag.SmallestImagePixelValue, signed, bitsStored));
            setTagNoNull(TagW.LargestImagePixelValue,
                getIntPixelValue(dicomObject, Tag.LargestImagePixelValue, signed, bitsStored));
            setTagNoNull(TagW.NumberOfFrames, getIntegerFromDicomElement(dicomObject, Tag.NumberOfFrames, null));
            setTagNoNull(TagW.OverlayRows, getIntegerFromDicomElement(dicomObject, Tag.OverlayRows, null));

            Integer samplesPerPixel = getIntegerFromDicomElement(dicomObject, Tag.SamplesPerPixel, null);
            setTagNoNull(TagW.SamplesPerPixel, samplesPerPixel);
            String photometricInterpretation = dicomObject.getString(Tag.PhotometricInterpretation);
            setTagNoNull(TagW.PhotometricInterpretation, photometricInterpretation);
            if (samplesPerPixel != null) {
                setTag(TagW.MonoChrome,
                    samplesPerPixel == 1 && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); //$NON-NLS-1$
            }

            setTagNoNull(TagW.Rows, getIntegerFromDicomElement(dicomObject, Tag.Rows, null));
            setTagNoNull(TagW.Columns, getIntegerFromDicomElement(dicomObject, Tag.Columns, null));

            int bitsAllocated = getIntegerFromDicomElement(dicomObject, Tag.BitsAllocated, 8);
            bitsAllocated = (bitsAllocated <= 8) ? 8 : ((bitsAllocated <= 16) ? 16 : 32);
            setTagNoNull(TagW.BitsAllocated, bitsAllocated);
            setTagNoNull(TagW.BitsStored,
                getIntegerFromDicomElement(dicomObject, Tag.BitsStored, (Integer) getTagValue(TagW.BitsAllocated)));
            setTagNoNull(TagW.PixelRepresentation, getIntegerFromDicomElement(dicomObject, Tag.PixelRepresentation, 0));

            setTagNoNull(TagW.PixelPaddingValue,
                getIntPixelValue(dicomObject, Tag.PixelPaddingValue, signed, bitsStored));
            setTagNoNull(TagW.PixelPaddingRangeLimit,
                getIntPixelValue(dicomObject, Tag.PixelPaddingRangeLimit, signed, bitsStored));

            setTagNoNull(TagW.MIMETypeOfEncapsulatedDocument, dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument));
        }
    }

    public static Integer getIntPixelValue(DicomObject ds, int tag, boolean signed, int stored) {
        DicomElement de = ds.get(tag);
        if (de == null) {
            return null;
        }
        int result;
        VR vr = de.vr();
        // Bug fix: http://www.dcm4che.org/jira/browse/DCM-460
        if (vr == VR.OB || vr == VR.OW) {
            result = ByteUtils.bytesLE2ushort(de.getBytes(), 0);
            if (signed) {
                if ((result & (1 << (stored - 1))) != 0) {
                    int andmask = (1 << stored) - 1;
                    int ormask = ~andmask;
                    result |= ormask;
                }
            }
        } else if ((!signed && vr != VR.US) || (signed && vr != VR.SS)) {
            vr = signed ? VR.SS : VR.US;
            result = vr.toInt(de.getBytes(), de.bigEndian());
        } else {
            result = de.getInt(false);
        }
        // Unsigned Short (0 to 65535) and Signed Short (-32768 to +32767)
        int minInValue = signed ? -(1 << (stored - 1)) : 0;
        int maxInValue = signed ? (1 << (stored - 1)) - 1 : (1 << stored) - 1;
        return result < minInValue ? minInValue : result > maxInValue ? maxInValue : result;
    }

    private static void validateDicomImageValues(HashMap<TagW, Object> dicomTagMap) {
        if (dicomTagMap != null) {

            Integer pixelRepresentation = (Integer) dicomTagMap.get(TagW.PixelRepresentation);
            boolean isPixelRepresentationSigned = (pixelRepresentation != null && pixelRepresentation != 0);

            // NOTE : Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values
            // shall be present but not both (@see Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module)

            DicomElement modalityLUTSequence = (DicomElement) dicomTagMap.get(TagW.ModalityLUTSequence);

            if (DicomMediaUtils.containsRequiredModalityLUTSequenceAttributes(modalityLUTSequence)) {
                DicomObject modalityLUTobj = modalityLUTSequence.getDicomObject(0);

                // TODO - should not create modality LUT here and prefer to do it in DicomImageElement Factory

                setTagNoNull(dicomTagMap, TagW.ModalityLUTData,
                    DicomMediaUtils.createLut(modalityLUTobj, isPixelRepresentationSigned));
                setTagNoNull(dicomTagMap, TagW.ModalityLUTType,
                    getStringFromDicomElement(modalityLUTobj, Tag.ModalityLUTType, null));
                setTagNoNull(dicomTagMap, TagW.ModalityLUTExplanation, // Optional Tag
                    getStringFromDicomElement(modalityLUTobj, Tag.LUTExplanation, null));
            }

            if (LOGGER.isDebugEnabled()) {

                // The output range of the Modality LUT Module depends on whether or not Rescale Slope and Rescale
                // Intercept or the Modality LUT Sequence are used.

                // In the case where Rescale Slope and Rescale Intercept are used, the output ranges from
                // (minimum pixel value*Rescale Slope+Rescale Intercept) to
                // (maximum pixel value*Rescale Slope+Rescale Intercept),
                // where the minimum and maximum pixel values are determined by Bits Stored and Pixel Representation.

                // In the case where the Modality LUT Sequence is used, the output range is from 0 to 2n-1 where n
                // is the third value of LUT Descriptor. This range is always unsigned.
                // The third value specifies the number of bits for each entry in the LUT Data. It shall take the value
                // 8 or 16. The LUT Data shall be stored in a format equivalent to 8 bits allocated when the number
                // of bits for each entry is 8, and 16 bits allocated when the number of bits for each entry is 16

                if (dicomTagMap.get(TagW.ModalityLUTData) != null) {
                    if (dicomTagMap.get(TagW.RescaleIntercept) != null) {
                        LOGGER.debug("Modality LUT Sequence shall NOT be present if Rescale Intercept is present"); //$NON-NLS-1$
                    }
                    if (dicomTagMap.get(TagW.ModalityLUTType) == null) {
                        LOGGER.debug("Modality Type is required if Modality LUT Sequence is present. "); //$NON-NLS-1$
                    }
                } else if (dicomTagMap.get(TagW.RescaleIntercept) != null) {
                    if (dicomTagMap.get(TagW.RescaleSlope) == null) {
                        LOGGER.debug("Modality Rescale Slope is required if Rescale Intercept is present."); //$NON-NLS-1$
                    } else if (dicomTagMap.get(TagW.RescaleType) == null) {
                        LOGGER.debug("Modality Rescale Type is required if Rescale Intercept is present."); //$NON-NLS-1$
                    }
                } else {
                    LOGGER.debug("Modality Rescale Intercept is required if Modality LUT Sequence is not present. "); //$NON-NLS-1$
                }
            }

            // NOTE : If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT
            // Table, but not both, may be applied to the Image for display. Inclusion of both indicates that multiple
            // alternative views may be presented. (@see Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module)

            DicomElement voiLUTSequence = (DicomElement) dicomTagMap.get(TagW.VOILUTSequence);

            if (DicomMediaUtils.containsRequiredVOILUTSequenceAttributes(voiLUTSequence)) {
                LookupTableJAI[] voiLUTsData = new LookupTableJAI[voiLUTSequence.countItems()];
                String[] voiLUTsExplanation = new String[voiLUTSequence.countItems()];

                boolean isOutModalityLutSigned = isPixelRepresentationSigned;

                // TODO - remove code below if VOI LUT is created in DicomImageElement Factory as described in next TODO

                // Evaluate outModality min value if signed
                LookupTableJAI modalityLookup = (LookupTableJAI) dicomTagMap.get(TagW.ModalityLUTData);

                Integer smallestPixelValue = (Integer) dicomTagMap.get(TagW.SmallestImagePixelValue);
                float minPixelValue = (smallestPixelValue == null) ? 0.0f : smallestPixelValue.floatValue();

                if (modalityLookup == null) {

                    Float intercept = (Float) dicomTagMap.get(TagW.RescaleIntercept);
                    Float slope = (Float) dicomTagMap.get(TagW.RescaleSlope);

                    slope = (slope == null) ? 1.0f : slope;
                    intercept = (intercept == null) ? 0.0f : intercept;

                    if ((minPixelValue * slope + intercept) < 0) {
                        isOutModalityLutSigned = true;
                    }
                } else {
                    int minInLutValue = modalityLookup.getOffset();
                    int maxInLutValue = modalityLookup.getOffset() + modalityLookup.getNumEntries() - 1;

                    if (minPixelValue >= minInLutValue && minPixelValue <= maxInLutValue
                        && modalityLookup.lookup(0, (int) minPixelValue) < 0) {
                        isOutModalityLutSigned = true;
                    }
                }

                // TODO - should not create VOI LUT here and prefer to do it in DicomImageElement Factory

                for (int i = 0; i < voiLUTSequence.countItems(); i++) {
                    DicomObject voiLUTobj = voiLUTSequence.getDicomObject(i);

                    voiLUTsData[i] = DicomMediaUtils.createLut(voiLUTobj, isOutModalityLutSigned);
                    voiLUTsExplanation[i] = getStringFromDicomElement(voiLUTobj, Tag.LUTExplanation, null);
                }

                setTag(dicomTagMap, TagW.VOILUTsData, voiLUTsData);
                setTag(dicomTagMap, TagW.VOILUTsExplanation, voiLUTsExplanation); // Optional Tag
            }

            if (LOGGER.isDebugEnabled()) {
                // If multiple items are present in VOI LUT Sequence, only one may be applied to the
                // Image for display. Multiple items indicate that multiple alternative views may be presented.

                // If multiple Window center and window width values are present, both Attributes shall have the same
                // number of values and shall be considered as pairs. Multiple values indicate that multiple alternative
                // views may be presented

                Float[] windowCenterDefaultTagArray = (Float[]) dicomTagMap.get(TagW.WindowCenter);
                Float[] windowWidthDefaultTagArray = (Float[]) dicomTagMap.get(TagW.WindowWidth);

                if (windowCenterDefaultTagArray == null && windowWidthDefaultTagArray != null) {
                    LOGGER.debug("VOI Window Center is required if Window Width is present"); //$NON-NLS-1$
                } else if (windowWidthDefaultTagArray == null && windowCenterDefaultTagArray != null) {
                    LOGGER.debug("VOI Window Width is required if Window Center is present"); //$NON-NLS-1$
                } else if (windowCenterDefaultTagArray != null && windowWidthDefaultTagArray != null
                    && windowWidthDefaultTagArray.length != windowCenterDefaultTagArray.length) {
                    LOGGER.debug("VOI Window Center and Width attributes have different number of values : {} // {}", //$NON-NLS-1$
                        windowCenterDefaultTagArray, windowWidthDefaultTagArray);
                }
            }
        }
    }

    private static float pixel2rescale(HashMap<TagW, Object> tagList, float pixelValue) {
        if (tagList != null) {
            LookupTableJAI lookup = (LookupTableJAI) tagList.get(TagW.ModalityLUTData);
            if (lookup != null) {
                if (pixelValue >= lookup.getOffset() && pixelValue <= lookup.getOffset() + lookup.getNumEntries() - 1) {
                    return lookup.lookup(0, (int) pixelValue);
                }
            } else {
                // value = pixelValue * rescale slope + intercept value
                Float slope = (Float) tagList.get(TagW.RescaleSlope);
                Float intercept = (Float) tagList.get(TagW.RescaleIntercept);
                if (slope != null || intercept != null) {
                    return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
                }
            }
        }
        return pixelValue;
    }

    private static void computeSlicePositionVector(HashMap<TagW, Object> tagList) {
        double[] patientPos = (double[]) tagList.get(TagW.ImagePositionPatient);
        if (patientPos != null && patientPos.length == 3) {
            double[] imgOrientation =
                ImageOrientation.computeNormalVectorOfPlan((double[]) tagList.get(TagW.ImageOrientationPatient));
            if (imgOrientation != null) {
                double[] slicePosition = new double[3];
                slicePosition[0] = imgOrientation[0] * patientPos[0];
                slicePosition[1] = imgOrientation[1] * patientPos[1];
                slicePosition[2] = imgOrientation[2] * patientPos[2];
                setTag(tagList, TagW.SlicePosition, slicePosition);
            }
        }
    }

    private void computeSUVFactor(DicomObject dicomObject, HashMap<TagW, Object> tagList, int index) {
        // From vendor neutral code at http://qibawiki.rsna.org/index.php?title=Standardized_Uptake_Value_%28SUV%29
        String modlality = (String) tagList.get(TagW.Modality);
        if ("PT".equals(modlality)) { //$NON-NLS-1$
            String correctedImage = getStringFromDicomElement(dicomObject, Tag.CorrectedImage, null);
            if (correctedImage != null && correctedImage.contains("ATTN") && correctedImage.contains("DECY")) { //$NON-NLS-1$ //$NON-NLS-2$
                double suvFactor = 0.0;
                String units = dicomObject.getString(Tag.Units);
                // DICOM $C.8.9.1.1.3 Units
                // The units of the pixel values obtained after conversion from the stored pixel values (SV) (Pixel
                // Data (7FE0,0010)) to pixel value units (U), as defined by Rescale Intercept (0028,1052) and
                // Rescale Slope (0028,1053). Defined Terms:
                // CNTS = counts
                // NONE = unitless
                // CM2 = centimeter**2
                // PCNT = percent
                // CPS = counts/second
                // BQML = Becquerels/milliliter
                // MGMINML = milligram/minute/milliliter
                // UMOLMINML = micromole/minute/milliliter
                // MLMING = milliliter/minute/gram
                // MLG = milliliter/gram
                // 1CM = 1/centimeter
                // UMOLML = micromole/milliliter
                // PROPCNTS = proportional to counts
                // PROPCPS = proportional to counts/sec
                // MLMINML = milliliter/minute/milliliter
                // MLML = milliliter/milliliter
                // GML = grams/milliliter
                // STDDEV = standard deviations
                if ("BQML".equals(units)) { //$NON-NLS-1$
                    Float weight = getFloatFromDicomElement(dicomObject, Tag.PatientWeight, 0.0f);
                    DicomElement seq = dicomObject.get(Tag.RadiopharmaceuticalInformationSequence);
                    if (weight != 0.0f && seq != null && seq.vr() == VR.SQ) {
                        DicomObject dcm = null;
                        try {
                            dcm = seq.getDicomObject(index);
                        } catch (Exception e) {
                            LOGGER.warn("", e); //$NON-NLS-1$
                        }
                        if (dcm != null) {
                            Float totalDose = getFloatFromDicomElement(dcm, Tag.RadionuclideTotalDose, null);
                            Float halfLife = getFloatFromDicomElement(dcm, Tag.RadionuclideHalfLife, null);
                            Date injectTime = getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartTime, null);
                            Date injectDateTime =
                                getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartDateTime, null);
                            Date acquisitionDateTime =
                                TagW.dateTime((Date) tagList.get(TagW.AcquisitionDate),
                                    (Date) tagList.get(TagW.AcquisitionTime));
                            Date scanDate = getDateFromDicomElement(dicomObject, Tag.SeriesDate, null);
                            if ("START".equals(dicomObject.getString(Tag.DecayCorrection)) && totalDose != null //$NON-NLS-1$
                                && halfLife != null && acquisitionDateTime != null
                                && (injectDateTime != null || (scanDate != null && injectTime != null))) {
                                double time = 0.0;
                                long scanDateTime =
                                    TagW.dateTime(scanDate, getDateFromDicomElement(dicomObject, Tag.SeriesTime, null))
                                        .getTime();
                                if (injectDateTime == null) {
                                    if (scanDateTime > acquisitionDateTime.getTime()) {
                                        // per GE docs, may have been updated during post-processing into new series
                                        String privateCreator = dicomObject.getString(0x00090010);
                                        Date privateScanDateTime = getDateFromDicomElement(dcm, 0x0009100d, null);
                                        if ("GEMS_PETD_01".equals(privateCreator) && privateScanDateTime != null) { //$NON-NLS-1$
                                            scanDate = privateScanDateTime;
                                        } else {
                                            scanDate = null;
                                        }
                                    }
                                    if (scanDate != null) {
                                        injectDateTime = TagW.dateTime(scanDate, injectTime);
                                        time = scanDateTime - injectDateTime.getTime();
                                    }

                                } else {
                                    time = scanDateTime - injectDateTime.getTime();
                                }
                                // Exclude negative value (case over midnight)
                                if (time > 0) {
                                    double correctedDose = totalDose * Math.pow(2, -time / (1000.0 * halfLife));
                                    // Weight convert in kg to g
                                    suvFactor = weight * 1000.0 / correctedDose;
                                }
                            }
                        }
                    }
                } else if ("CNTS".equals(units)) { //$NON-NLS-1$
                    String privateTagCreator = dicomObject.getString(0x70530010);
                    double privateSUVFactor = dicomObject.getDouble(0x70531000, 0.0);
                    if ("Philips PET Private Group".equals(privateTagCreator) && privateSUVFactor != 0.0) { //$NON-NLS-1$
                        suvFactor = privateSUVFactor;
                        // units= "g/ml";
                    }
                } else if ("GML".equals(units)) { //$NON-NLS-1$
                    suvFactor = 1.0;
                    // UNIT
                    // String unit = dicomObject.getString(Tag.SUVType);

                }
                if (suvFactor != 0.0) {
                    setTag(tagList, TagW.SuvFactor, suvFactor);
                }
            }
        }
    }

    public static String getStringFromDicomElement(DicomObject dicom, int tag, String defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        }
        String[] s = element.getStrings(dicom.getSpecificCharacterSet(), false);
        if (s.length == 1) {
            return s[0];
        }
        if (s.length == 0) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder sb = new StringBuilder(s[0]);
        for (int i = 1; i < s.length; i++) {
            sb.append("\\" + s[i]); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public static String[] getStringArrayFromDicomElement(DicomObject dicom, int tag, String[] defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        }
        return element.getStrings(dicom.getSpecificCharacterSet(), false);
    }

    public static Date getDateFromDicomElement(DicomObject dicom, int tag, Date defaultValue) {
        DicomElement element = (dicom != null) ? dicom.get(tag) : null;

        if (element != null && !element.isEmpty()) {
            try {
                return element.getDate(false);
            } catch (Exception e) {
                // Value not valid according to DICOM standard
                LOGGER.error("Cannot parse date {}", element.toString()); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static Float[] getFloatArrayFromDicomElement(DicomObject dicom, int tag, Float[] defaultValue) {
        DicomElement element = (dicom != null) ? dicom.get(tag) : null;

        if (element != null && !element.isEmpty()) {
            float[] fResults = element.getFloats(false);

            if (fResults != null && fResults.length > 0) {
                List<Float> fResultList = new ArrayList<Float>(fResults.length);
                for (float result : fResults) {
                    fResultList.add(result);

                }
                return fResultList.toArray(new Float[fResultList.size()]);
            }
        }
        return defaultValue;
    }

    public static Float getFloatFromDicomElement(DicomObject dicom, int tag, Float defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getFloat(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public static Integer getIntegerFromDicomElement(DicomObject dicom, int tag, Integer defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getInt(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public static Double getDoubleFromDicomElement(DicomObject dicom, int tag, Double defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getDouble(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public boolean containTag(int id) {
        for (Iterator<TagW> it = tags.keySet().iterator(); it.hasNext();) {
            if (it.next().getId() == id) {
                return true;
            }
        }
        return false;
    }

    public int getBitsStored() {
        return bitsStored;
    }

    @Override
    public void reset() {
        // Reset must be called only after reading the header, because closing imageStream does not let through
        // getTile(x,y) read image data.
        // unlock file to be deleted on exit
        FileUtil.safeClose(imageStream);
        imageStream = null;
        dicomObject = null;
        super.reset();
    }

    public boolean writeDICOM(File destination) throws Exception {
        DicomOutputStream out = null;
        DicomInputStream dis = null;
        try {
            out = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(destination)));
            if (uri.toString().startsWith("file:/")) { //$NON-NLS-1$
                dis = new DicomInputStream(new File(uri));
            } else {
                dis = new DicomInputStream(uri.toURL().openStream());
            }
            // dis.setHandler(new StopTagInputHandler(Tag.PixelData));
            DicomObject dcm = dis.readDicomObject();
            out.writeDicomFile(dcm);
        } catch (IOException e) {
            LOGGER.warn("", e); //$NON-NLS-1$
            destination.delete();
            return false;
        } finally {
            FileUtil.safeClose(out);
            FileUtil.safeClose(dis);
        }
        return true;
    }

    public DicomObject getDicomObject() {
        if (dicomObject == null) {
            readMediaTags();
            DicomObject dcm = dicomObject;
            reset();
            return dcm;
        }
        return dicomObject;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getKey() instanceof Integer) {
            if (dicomObject == null) {
                readMediaTags();
            }
            int frame = (Integer) media.getKey();
            if (frame >= 0 && frame < numberOfFrame && bitsStored > 0) {
                // read as tiled rendered image
                LOGGER.debug("read dicom image frame: {} sopUID: {}", frame, dicomObject.getString(Tag.SOPInstanceUID)); //$NON-NLS-1$
                RenderedImage buffer = null;
                if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
                    if (jpipReader == null) {
                        ImageReaderFactory f = ImageReaderFactory.getInstance();
                        jpipReader = f.getReaderForTransferSyntax(tsuid);
                    }
                    setTagNoNull(TagW.PixelDataProviderURL, dicomObject.getString(Tag.PixelDataProviderURL));
                    MediaElement[] elements = getMediaElement();
                    if (elements != null && elements.length > frame) {
                        jpipReader.setInput(elements);
                        buffer = jpipReader.readAsRenderedImage(frame, null);
                    }
                } else {
                    buffer = readAsRenderedImage(frame, null);
                }
                PlanarImage img = null;
                if (buffer != null) {
                    // Bug fix: CLibImageReader and J2KImageReaderCodecLib (imageio libs) do not handle negative values
                    // for short data. They convert signed short to unsigned short.
                    if (getDataType() == DataBuffer.TYPE_SHORT
                        && buffer.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
                        img =
                            RectifyUShortToShortDataDescriptor
                                .create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
                    } else if (ImageUtil.isBinary(buffer.getSampleModel())) {
                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(buffer);
                        // Tile size are set in this operation
                        img = JAI.create("formatbinary", pb, null); //$NON-NLS-1$
                    } else if (buffer.getTileWidth() != ImageFiler.TILESIZE
                        || buffer.getTileHeight() != ImageFiler.TILESIZE) {
                        img = ImageFiler.tileImage(buffer);
                    } else {
                        img = NullDescriptor.create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
                    }
                }
                return img;
            }
        }
        return null;
    }

    private MediaElement getSingleImage() {
        MediaElement[] elements = getMediaElement();
        if (elements != null && elements.length > 0) {
            return elements[0];
        }
        return null;
    }

    @Override
    public MediaElement getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        if (image == null) {
            if (readMediaTags()) {
                if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
                    image = new MediaElement[] { new DicomVideoElement(this, null) };
                } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
                    image = new MediaElement[] { new DicomEncapDocElement(this, null) };
                } else {
                    if (numberOfFrame > 0) {
                        image = new MediaElement[numberOfFrame];
                        for (int i = 0; i < image.length; i++) {
                            image[i] = new DicomImageElement(this, i);
                        }
                        if (numberOfFrame > 1) {
                            // IF enhanced DICOM, instance number can be overridden later
                            // IF simple Multiframe instance number is necessary
                            for (int i = 0; i < image.length; i++) {
                                image[i].setTag(TagW.InstanceNumber, i + 1);
                            }
                        }
                    } else {
                        String modality = (String) getTagValue(TagW.Modality);
                        boolean ps =
                            modality != null
                                && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (ps) {
                            image = new MediaElement[1];
                            image[0] = new DicomSpecialElement(this, null);
                        } else {
                            // Corrupted image => should have one frame
                            image = new MediaElement[0];
                        }
                    }
                }
            }
        }
        return image;
    }

    @Override
    public MediaSeries getMediaSeries() {
        Series series = null;
        if (readMediaTags()) {
            String seriesUID = (String) getTagValue(TagW.SeriesInstanceUID);
            series = buildSeries(seriesUID);
            writeMetaData(series);
            // no need to apply splitting rules
            // also no model
            MediaElement[] elements = getMediaElement();
            if (elements != null) {
                for (MediaElement media : elements) {
                    series.addMedia(media);
                }
            }

        }
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        return numberOfFrame;
    }

    @Override
    public String getMediaFragmentMimeType(Object key) {
        return mimeType;
    }

    @Override
    public HashMap<TagW, Object> getMediaFragmentTags(Object key) {
        if (key instanceof Integer) {
            if ((Integer) key > 0) {
                HashMap<TagW, Object> tagList = (HashMap<TagW, Object>) tags.clone();
                if (writePerFrameFunctionalGroupsSequence(tagList, dicomObject, (Integer) key)) {
                    validateDicomImageValues(tagList);
                    computeSlicePositionVector(tagList);
                }
                return tagList;
            }
        }
        return tags;
    }

    private boolean writePerFrameFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject dicomObject,
        int index) {
        if (dicomObject != null && tagList != null) {
            DicomElement seq = dicomObject.get(Tag.PerFrameFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(index);
                } catch (Exception e) {
                    LOGGER.warn("", e); //$NON-NLS-1$
                }
                if (dcm != null) {
                    writeFunctionalGroupsSequence(tagList, dcm);
                    return true;
                }
            }
        }
        return false;
    }

    private void writeFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject dcm) {
        if (dcm != null && tagList != null) {
            if (dcm != null) {

                DicomElement sequenceElt = dcm.get(Tag.PlanePositionSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    setTagNoNull(tagList, TagW.ImagePositionPatient,
                        sequenceElt.getDicomObject(0).getDoubles(Tag.ImagePositionPatient, (double[]) null));
                }

                sequenceElt = dcm.get(Tag.PlaneOrientationSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    double[] imgOrientation =
                        sequenceElt.getDicomObject(0).getDoubles(Tag.ImageOrientationPatient, (double[]) null);
                    setTagNoNull(tagList, TagW.ImageOrientationPatient, imgOrientation);
                    setTagNoNull(tagList, TagW.ImageOrientationPlane,
                        ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(imgOrientation));
                }
                // TODO
                // seq = dcm.get(Tag.ReferencedImageSequence);
                // if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                // DicomObject ref = seq.getDicomObject(0);
                // setTagNoNull(tagList, TagW.PurposeOfReferenceCodeSequence, getFloatFromDicomElement(ref,
                // Tag.PurposeOfReferenceCodeSequence, null));
                //
                // }

                /**
                 * Specifies the attributes of the Pixel Value Transformation Functional Group. This is equivalent with
                 * the Modality LUT transformation in non Multi-frame IODs. It constrains the Modality LUT
                 * transformation step in the grayscale rendering pipeline to be an identity transformation.
                 * 
                 * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.9b Identity Pixel Value Transformation
                 */
                sequenceElt = dcm.get(Tag.PixelValueTransformationSequence);

                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    // Only one single item is permitted in this sequence
                    DicomObject pixelValueTransformation = sequenceElt.getDicomObject(0);

                    // Overrides Modality LUT Transformation attributes only if sequence is consistent
                    if (DicomMediaUtils.containsRequiredModalityLUTRescaleAttributes(pixelValueTransformation)) {
                        setTagNoNull(tagList, TagW.RescaleSlope,
                            getFloatFromDicomElement(pixelValueTransformation, Tag.RescaleSlope, null));
                        setTagNoNull(tagList, TagW.RescaleIntercept,
                            getFloatFromDicomElement(pixelValueTransformation, Tag.RescaleIntercept, null));
                        setTagNoNull(tagList, TagW.RescaleType,
                            getStringFromDicomElement(pixelValueTransformation, Tag.RescaleType, null));
                        setTagNoNull(tagList, TagW.ModalityLUTSequence, null); // must be identity transformation
                    } else {
                        LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                            TagUtils.toString(Tag.PixelValueTransformationSequence));
                    }
                }

                /**
                 * Specifies the attributes of the Frame VOI LUT Functional Group. It contains one or more sets of
                 * linear or sigmoid window values and/or one or more sets of lookup tables
                 * 
                 * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.10b Frame VOI LUT With LUT Macro
                 */

                sequenceElt = dcm.get(Tag.FrameVOILUTSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    // Only one single item is permitted in this sequence
                    DicomObject frameVOILUTSequence = sequenceElt.getDicomObject(0);

                    // Overrides VOI LUT Transformation attributes only if sequence is consistent
                    if (DicomMediaUtils.containsRequiredVOILUTAttributes(frameVOILUTSequence)) {
                        setTagNoNull(tagList, TagW.WindowWidth,
                            getFloatArrayFromDicomElement(frameVOILUTSequence, Tag.WindowWidth, null));
                        setTagNoNull(tagList, TagW.WindowCenter,
                            getFloatArrayFromDicomElement(frameVOILUTSequence, Tag.WindowCenter, null));
                        setTagNoNull(tagList, TagW.WindowCenterWidthExplanation,
                            getStringArrayFromDicomElement(frameVOILUTSequence, Tag.WindowCenterWidthExplanation, null));
                        setTagNoNull(tagList, TagW.VOILutFunction,
                            getStringFromDicomElement(frameVOILUTSequence, Tag.VOILUTFunction, null));
                        setTagNoNull(TagW.VOILUTSequence, frameVOILUTSequence.get(Tag.VOILUTSequence));
                    } else {
                        LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                            TagUtils.toString(Tag.FrameVOILUTSequence));
                    }
                }

                sequenceElt = dcm.get(Tag.PixelMeasuresSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject measure = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.PixelSpacing, measure.getDoubles(Tag.PixelSpacing, (double[]) null));
                    setTagNoNull(tagList, TagW.SliceThickness,
                        getFloatFromDicomElement(measure, Tag.SliceThickness, null));
                }

                // Identifies the characteristics of this frame. Only a single Item shall be permitted in this sequence.
                sequenceElt = dcm.get(Tag.MRImageFrameTypeSequence);
                if (sequenceElt == null) {
                    sequenceElt = dcm.get(Tag.CTImageFrameTypeSequence);
                }
                if (sequenceElt == null) {
                    sequenceElt = dcm.get(Tag.MRSpectroscopyFrameTypeSequence);
                }
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    // Type of Frame. A multi-valued attribute analogous to the Image Type (0008,0008).
                    // Enumerated Values and Defined Terms are the same as those for the four values of the Image Type
                    // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and C.8.13.3.1.1.
                    setTagNoNull(tagList, TagW.FrameType, frame.getString(Tag.FrameType));
                }

                sequenceElt = dcm.get(Tag.FrameContentSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.FrameAcquisitionNumber,
                        getIntegerFromDicomElement(frame, Tag.FrameAcquisitionNumber, null));
                    setTagNoNull(tagList, TagW.StackID, frame.getString(Tag.StackID));
                    setTagNoNull(tagList, TagW.InstanceNumber,
                        getIntegerFromDicomElement(frame, Tag.InStackPositionNumber, null));
                }

                // TODO implement: Frame Pixel Shift, Pixel Intensity Relationship LUT, Real World Value Mapping

                // setTagNoNull(tagList, TagW.PixelSpacingCalibrationDescription,
                // dicomObject.getString(Tag.PixelSpacingCalibrationDescription));
                // setTagNoNull(tagList, TagW.Units, dicomObject.getString(Tag.Units));

                // Frame Display Shutter Sequence (0018,9472)
                // Display Shutter Macro Table C.7-17A in PS 3.3
                sequenceElt = dcm.get(Tag.FrameDisplayShutterSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    Area shape = buildShutterArea(frame);
                    if (shape != null) {
                        setTagNoNull(tagList, TagW.ShutterFinalShape, shape);
                        Integer psVal = getIntegerFromDicomElement(frame, Tag.ShutterPresentationValue, null);
                        setTagNoNull(tagList, TagW.ShutterPSValue, psVal);
                        float[] rgb =
                            DisplayShutterModule.convertToFloatLab(frame.getInts(
                                Tag.ShutterPresentationColorCIELabValue, (int[]) null));
                        Color color =
                            rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb,
                                (int[]) null);
                        setTagNoNull(tagList, TagW.ShutterRGBColor, color);
                    }
                }

                sequenceElt = dcm.get(Tag.FrameAnatomySequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.ImageLaterality, frame.getString(Tag.FrameLaterality));
                }
            }
        }
    }

    private Area buildShutterArea(DicomObject dcmObject) {
        Area shape = null;
        String shutterShape = getStringFromDicomElement(dcmObject, Tag.ShutterShape, null);
        if (shutterShape != null) {
            // RECTANGULAR is legal
            if (shutterShape.contains("RECTANGULAR") || shutterShape.contains("RECTANGLE")) { //$NON-NLS-1$ //$NON-NLS-2$
                Rectangle2D rect = new Rectangle2D.Double();
                rect.setFrameFromDiagonal(getIntegerFromDicomElement(dcmObject, Tag.ShutterLeftVerticalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterUpperHorizontalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterRightVerticalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterLowerHorizontalEdge, 0));
                shape = new Area(rect);

            }
            if (shutterShape.contains("CIRCULAR")) { //$NON-NLS-1$
                int[] centerOfCircularShutter = dcmObject.getInts(Tag.CenterOfCircularShutter, (int[]) null);
                if (centerOfCircularShutter != null && centerOfCircularShutter.length >= 2) {
                    Ellipse2D ellipse = new Ellipse2D.Double();
                    int radius = getIntegerFromDicomElement(dcmObject, Tag.RadiusOfCircularShutter, 0);
                    // Thanks Dicom for reversing x,y by row,column
                    ellipse.setFrameFromCenter(centerOfCircularShutter[1], centerOfCircularShutter[0],
                        centerOfCircularShutter[1] + radius, centerOfCircularShutter[0] + radius);
                    if (shape == null) {
                        shape = new Area(ellipse);
                    } else {
                        shape.intersect(new Area(ellipse));
                    }
                }
            }
            if (shutterShape.contains("POLYGONAL")) { //$NON-NLS-1$
                int[] points = dcmObject.getInts(Tag.VerticesOfThePolygonalShutter, (int[]) null);
                if (points != null) {
                    Polygon polygon = new Polygon();
                    for (int i = 0; i < points.length / 2; i++) {
                        // Thanks Dicom for reversing x,y by row,column
                        polygon.addPoint(points[i * 2 + 1], points[i * 2]);
                    }
                    if (shape == null) {
                        shape = new Area(polygon);
                    } else {
                        shape.intersect(new Area(polygon));
                    }
                }
            }
        }
        return shape;
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
    }

    @Override
    public void close() {
        reset();
        dispose();
    }

    public static boolean hasPlatformNativeImageioCodecs() {
        return ImageIO.getImageReadersByFormatName("JPEG-LS").hasNext(); //$NON-NLS-1$
    }

    @Override
    public Codec getCodec() {
        return BundleTools.getCodec(DicomMediaIO.MIMETYPE, DicomCodec.NAME);
    }

    @Override
    public String[] getReaderDescription() {
        String[] desc = new String[3];
        desc[0] = "DICOM Codec: " + DicomCodec.NAME; //$NON-NLS-1$
        ImageReader imgReader = null;
        try {
            imgReader =
                ImageReaderFactory.getInstance().getReaderForTransferSyntax(
                    (String) getTagValue(TagW.TransferSyntaxUID));
        } catch (Exception e1) {
            // Do nothing
        }
        if (imgReader != null) {
            desc[1] = "Image Reader Class: " + imgReader.getClass().getName(); //$NON-NLS-1$
            try {
                desc[2] = "Image Format: " + imgReader.getFormatName(); //$NON-NLS-1$
            } catch (IOException e) {
                desc[2] = "Image Format: unknown"; //$NON-NLS-1$
            }

        }
        if (desc[1] == null) {
            desc[1] = Messages.getString("DicomMediaIO.msg_no_reader") + tsuid; //$NON-NLS-1$
        }
        return desc;
    }

    public Series buildSeries(String seriesUID) {
        if (IMAGE_MIMETYPE.equals(mimeType)) {
            return new DicomSeries(seriesUID);
        } else if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
            return new DicomVideoSeries(seriesUID);
        } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
            return new DicomEncapDocSeries(seriesUID);
        }
        return new DicomSeries(seriesUID);
    }

}

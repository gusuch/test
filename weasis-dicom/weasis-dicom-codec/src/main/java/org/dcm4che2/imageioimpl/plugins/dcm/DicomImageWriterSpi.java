/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4che2.imageioimpl.plugins.dcm;

import java.util.Locale;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;

/** Provide information about the dicom image writers */
public class DicomImageWriterSpi extends ImageWriterSpi {
    private static final String[] formatNames = { "dicom", "DICOM" };
    private static final String[] suffixes = { "dcm", "dic", "dicm", "dicom" };
    private static final String[] MIMETypes = { "application/dicom" };
    private static final String[] readers = { "org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader" };

    private static String vendor;
    private static String version;
    static {
        Package p = DicomImageWriterSpi.class.getPackage();
        vendor = maskNull(p.getImplementationVendor(), "");
        version = maskNull(p.getImplementationVersion(), "");
    }

    private static String maskNull(String s, String def) {
        return s != null ? s : def;
    }

    public DicomImageWriterSpi() {
        super(vendor, version, formatNames, suffixes, MIMETypes,
            "org.dcm4che2.imageioimpl.plugins.dcm.DicomImageWriter", STANDARD_OUTPUT_TYPE, readers, false, null, null,
            null, null, false, null, null, null, null);
    }

    /** Indicate the DICOM can encapsulate the given type */
    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        return true;
    }

    /** Create a dicom image writer */
    @Override
    public ImageWriter createWriterInstance(Object extension) {
        return new DicomImageWriter(this);
    }

    /** Get the description of this image writer type. */
    @Override
    public String getDescription(Locale locale) {
        return "DICOM Image Writer";
    }

}

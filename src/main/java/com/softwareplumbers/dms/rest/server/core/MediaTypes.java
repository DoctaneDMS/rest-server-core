/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.core.MediaType;

/** Helper Methods for handling MediaType objects.
 *
 * @author jonathan.local
 */
public class MediaTypes {
    
    private static MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap() {{ 
       this.addMimeTypes("type=application/vnd.ms-outlook exts=msg");
       this.addMimeTypes("type=application/msword exts=doc,dot");
       this.addMimeTypes("type=application/vnd.openxmlformats-officedocument.wordprocessingml.document exts=docx,dotx");
    }};
    
    /** Microsoft Word XML media type (aka DOCX) */
    public static final MediaType MICROSOFT_WORD_XML = MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); 
    /** Microsoft Word media type (aka DOC) */
    public static final MediaType MICROSOFT_WORD = MediaType.valueOf("application/msword"); 
    /** Microsoft Outlook message media type (aka MSG) */
    public static final MediaType MICROSOFT_OUTLOOK = MediaType.valueOf("application/vnd.ms-outlook"); 
    /** Zip media type */
    public static final MediaType ZIP = MediaType.valueOf("application/zip");
    
    /** List of Open Office XML types which the Tika Open Office XML parser can handle */
    private static List<MediaType> OPEN_OFFICE_XML_TYPES = Arrays.asList(MICROSOFT_WORD_XML);
    /** List of Open Office XML types which the Tika Office parser can handle */
    private static List<MediaType> OFFICE_TYPES = Arrays.asList(MICROSOFT_OUTLOOK, MICROSOFT_WORD);

   /** Test if one media type is a subset of another.
    * 
    * Media types overlap; for example '*//*' would be a superset of all media types, and 'image/*' is a superset
    * of 'image/png' and 'image/git'.
    * 
    * @param superset
    * @param subset
    * @return true if 'subset' is a subset of 'superset'.
    */    
    public static boolean isSubsetType(MediaType superset, MediaType subset) {
        if (superset.isWildcardType()) return true;
        if (superset.getType().equals(subset.getType())) {
            if (superset.isWildcardSubtype()) return true;
            if (superset.getSubtype().equals(subset.getSubtype())) {
                // do something with parameters?
                return true;
            }
        }
        return false;
    }
    
    /** Get the media type that will be returned based on the preferred and requested media types 
     */
    public static final MediaType getPreferredMediaType(List<MediaType> requestedMediaTypes, List<MediaType> preferredMediaTypes) throws InvalidContentType {
        for (MediaType preferred : preferredMediaTypes)
            for (MediaType requested : requestedMediaTypes)
                if (isSubsetType(requested, preferred)) return preferred;
        throw new InvalidContentType(requestedMediaTypes, preferredMediaTypes);
    }
    
    /** Get the set of acceptable media types, constrained by the additional constraint.
     * 
     * Basically we have a list of acceptable media types furnished by the client (...maybe by the web browser) and an
     * additional media type that can be passed in separately. If these are completely incompatible, (for example, 
     * application/pdf and text/html) then there is no possible type that satisfies both. However if either the list or
     * the additional constraint contains wildcard types, the picture is more complicated. This method attempts to return
     * a list of media types that satisfies both the additional constraint, and is compatible with one or more of the
     * types specified in the original list.
     * 
     * @param requestedMediaTypes
     * @param addlConstraint
     * @return 
     */
    public static final List<MediaType> getAcceptableMediaTypes(List<MediaType> requestedMediaTypes, MediaType addlConstraint) {
        LinkedList<MediaType> result = new LinkedList<>();
        for (MediaType requested : requestedMediaTypes) {
            if (isSubsetType(requested, addlConstraint)) result.add(addlConstraint);
            if (isSubsetType(addlConstraint, requested)) result.add(requested);
        }
        return result;
    }
    
    /** Attempt to extract a MediaType from the given filename.
     * 
     * @param name
     * @return A Media Type. application/octet-stream if nothing better can be found.
     */
    public static MediaType getTypeFromFilename(String name) {
        return MediaType.valueOf(fileTypeMap.getContentType(name));
    }
    
    /** Test if the given type/filename is recognized as an Open Office XML type.
     * 
     * @param type
     * @param name
     * @return true if type or name indicate that the resource is an open office XML document.
     */
    public static boolean isOpenOfficeXMLDoc(MediaType type, String name) {
        return OPEN_OFFICE_XML_TYPES.contains(type) || name != null && OPEN_OFFICE_XML_TYPES.contains(getTypeFromFilename(name));
    }
    
    /** Test if the given type/filename is recognized as a legacy Office type 
     * 
     * @param type
     * @param name
     * @return true if type or name indicate that the resource is an legacy office document.
     */
    public static boolean isLegacyOfficeDoc(MediaType type, String name) {
        return OFFICE_TYPES.contains(type) || name != null && OFFICE_TYPES.contains(getTypeFromFilename(name));
    }
    
  
    /** *  Compute best media type from supplied type and filename 
     *
     * Computes a media type from the supplied filename.If the computed type is a subset of the supplied type,
     * use the computed type.If the media type is 'application/octet-stream' then regard any 'application' type
     * as a subset even it it isn't really.
     * 
     * @param type Supplied media type
     * @param filename Filename to compute media type from
     * @return The most specific of the supplied type and the computed type
     */
    public static MediaType getComputedMediaType(MediaType type, String filename) {
        MediaType typeFromName = getTypeFromFilename(filename);
        if (isSubsetType(type, typeFromName)) return typeFromName;
        if (type.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE) && typeFromName.getType().equals("application")) return typeFromName;
        return type;
    }
}

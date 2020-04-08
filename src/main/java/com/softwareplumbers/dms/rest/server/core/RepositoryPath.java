/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.abstractpattern.Pattern;
import com.softwareplumbers.common.abstractpattern.parsers.Parsers;
import com.softwareplumbers.common.immutablelist.AbstractImmutableList;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.dms.Constants;
import java.util.Comparator;
import java.util.Optional;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
public class RepositoryPath extends AbstractImmutableList<RepositoryPath.Element, RepositoryPath> {
    
    private static XLogger LOG = XLoggerFactory.getXLogger(RepositoryPath.class);
    
    public enum ElementType {
        OBJECT_ID,
        DOCUMENT_PATH,
        PART_PATH,
        PART_ROOT
    }
    
    public static class Element implements Comparable<Element> {
        
        public final ElementType type;
        
        public Element(ElementType type) {
            this.type = type;
        }
        
        @Override
        public int compareTo(Element other) {
            return type.compareTo(other.type);
        }

        @Override        
        public boolean equals(Object other) {
            return other instanceof Element && 0 == compareTo((Element)other);
        }
        
        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }
    
    public static class PartRoot extends Element {
        
        public PartRoot() {
            super(ElementType.PART_ROOT);
        }
        
        public String toString() {
            return "~";
        }
    }
    
    public static class NamedElement extends Element {
        
        public final String name;
        public final Pattern pattern;
        
        public NamedElement(ElementType type, String name) {
            super(type);
            this.name = name;
            this.pattern = Parsers.parseUnixWildcard(name);            
        }
        
        @Override
        public int compareTo(Element other) {
            int result = super.compareTo(other);
            if (result == 0) {
                result = name.compareTo(((NamedElement)other).name);
            }
            return result;
        }
        
        @Override
        public int hashCode() {
            return name.hashCode() ^ super.hashCode();
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    public static class IdElement extends Element {
        
        private static final Comparator<String> VERSION_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());
                
        public final String id;
        public final String version;

        
        public IdElement(String id, String version) {
            super(ElementType.OBJECT_ID);
            this.id = id;
            this.version = version;
        }
        
        @Override
        public int compareTo(Element other) {
            int result = super.compareTo(other);
            if (result == 0) {
                IdElement otherId = (IdElement)other;
                result = id.compareTo(otherId.id);
                if (result == 0) {
                    result = VERSION_COMPARATOR.compare(version, otherId.version);
                }
            }
            return result;
        }
        
        @Override
        public int hashCode() {
            return id.hashCode() ^ (version == null ? 0 : version.hashCode()) ^ super.hashCode();
        }
        
        @Override
        public String toString() {
            if (version == null)
                return "~" + id;
            else
                return "~" + id + "@" + version;
        }
    }    
    
    public static class VersionedElement extends NamedElement {
        
        private static final Comparator<String> VERSION_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());
        
        public final String version;
        
        public VersionedElement(ElementType type, String name, String version) {
            super(type, name);
            this.version = version;
        }
        
        @Override
        public int compareTo(Element other) {
            int result = super.compareTo(other);
            if (result == 0) {
                result = VERSION_COMPARATOR.compare(version, ((VersionedElement)other).version);
            }
            return result;
        }
        
        @Override
        public int hashCode() {
            return version.hashCode() ^ super.hashCode();
        }
        
        @Override
        public String toString() {
            if (version == null) 
                return name;
            else
                return name + "@" + version;
        }
    }
    

    
    public static final RepositoryPath ROOT = new RepositoryPath(null, null) {
        public boolean isEmpty() { return true; }
    };

    private RepositoryPath(RepositoryPath parent, Element part) {
        super(parent, part);
    }

    @Override
    public RepositoryPath getEmpty() {
        return ROOT;
    }

    @Override
    public RepositoryPath add(Element element) {
        return new RepositoryPath(this, element);
    }
    
    
    
    public RepositoryPath addDocumentPath(String nameVersion) {
        String[] parts = split(nameVersion, "@");
        String name = unescape(parts[0]);
        String version = parts.length > 1 ? unescape(parts[1]) : null;
        return add(new VersionedElement(ElementType.DOCUMENT_PATH, name, version));
    }

    public RepositoryPath addPartPath(String name) {
        return add(new NamedElement(ElementType.PART_PATH, name));
    }
    
    public RepositoryPath addId(String idVersion) {
        String[] parts = split(idVersion, "@");
        String id = unescape(parts[0]);
        String version = parts.length > 1 ? unescape(parts[1]) : null;
        return add(new IdElement(id, version));
    }

    public RepositoryPath addRootPart() {
        return add(new PartRoot());
    }
    
    private static final String unescape(String escaped) {
        String regexEscape = "\\\\";
        String escape = "\\";
        return escaped
            .replaceAll("(?<!"+ regexEscape +")" + regexEscape, "")
            .replaceAll(regexEscape + regexEscape, escape);
    }
    
 	private static String[] split(String toParse, String separator) {
        String regexEscape = "\\\\";
        String escape = "\\";
        return toParse.split("(?<!"+ regexEscape +")" + separator);
	}

    
    /** Detect whether a path element is an id.
     *
     * An element is an id if it is at least two characters long, starts with a ~, and the tilde is
     * not doubled (~~ is an escaped ~)
     *
     * @param element
     * @return true if element is an id
     */
    public static boolean isId(String element) {
        if (element == null) return false;
        return element.length() > 1 && element.startsWith("~") && !element.startsWith("~~");
    }

    public static boolean isPartDelimeter(String element) {
        if (element == null) return false;
        return element.equals("~");
    }
    
    public static RepositoryPath valueOf(String path) {
        RepositoryPath result = ROOT;
        boolean seenPartDelimeter = false;
        try {
            for (String pathElement : split(path, "/")) {
                if (pathElement.length() == 0) continue;
                if (isPartDelimeter(pathElement)) {
                    result = result.addRootPart();
                    seenPartDelimeter = true;
                    continue;
                } 
                if (isId(pathElement)) {
                    String id = pathElement.substring(1);
                    result = result.addId(id);
                    continue;
                } 
                if (seenPartDelimeter) {
                    result = result.addPartPath(pathElement);
                    continue;
                } 
                result = result.addDocumentPath(pathElement);
            }
        } catch (RuntimeException e) {
            LOG.catching(e);    
        }
        return result;
    }
    
    public RepositoryPath getDocumentPath() {
        int ix = indexOf(e -> e.type == ElementType.PART_ROOT);
        return ix < 0 ? this : left(ix);
    }
    
    public RepositoryPath getPartPath() {
        int ix = indexOf(e -> e.type == ElementType.PART_ROOT);
        return ix < 0 ? ROOT : rightFromStart(ix);
    }

    public RepositoryPath getVersioned() {
        int ix = indexOf(e -> e.type == ElementType.DOCUMENT_PATH && ((VersionedElement)e).version != null || e.type == ElementType.OBJECT_ID && ((IdElement)e).version != null);
        return left(ix+1);        
    }
    
    public RepositoryPath getAfterVersion() {
        int ix = indexOf(e -> e.type == ElementType.DOCUMENT_PATH && ((VersionedElement)e).version != null || e.type == ElementType.OBJECT_ID && ((IdElement)e).version != null);
        return ix < 0 ? ROOT : rightFromStart(ix+1);
    }

    public RepositoryPath getSimplePath() {
        int ix = indexOf(e -> e.type != ElementType.OBJECT_ID && e.type != ElementType.PART_ROOT && !((NamedElement)e).pattern.isSimple());
        return left(ix);        
    }

    public RepositoryPath getQueryPath() {
        int ix = indexOf(e -> e.type != ElementType.OBJECT_ID && e.type != ElementType.PART_ROOT && !((NamedElement)e).pattern.isSimple());
        return ix < 0 ? ROOT : rightFromStart(ix);        
    }
    
    public Optional<IdElement> getRootId() {
        if (isEmpty()) return Optional.empty();
        Element e = get(0);
        if (e.type == ElementType.OBJECT_ID)
            return Optional.of((IdElement)e);
        else
            return Optional.empty();
    }
    
    public RepositoryPath afterRootId() {
        if (isEmpty()) return ROOT;
        Element e = get(0);
        if (e.type == ElementType.OBJECT_ID)
            return rightFromStart(1);
        else
            return this;        
    }

    public Optional<IdElement> getId() {
        int ix = indexOf(e -> e.type == ElementType.OBJECT_ID);
        return ix < 0 ? Optional.empty() : Optional.of((IdElement)get(ix));                
    }
 
    public RepositoryPath beforeId() {
        int ix = indexOf(e -> e.type == ElementType.OBJECT_ID);
        return ix < 0 ? this : left(ix);                
    }
    
    
    
    public QualifiedName getDocumentName() {
        return apply(QualifiedName.ROOT, (qn, e)->e.type==ElementType.DOCUMENT_PATH ? qn.add((((NamedElement)e).name)) : qn);
    }
    
    public Optional<QualifiedName> getPartName() {
        RepositoryPath partPath = getPartPath();
        if (partPath.isEmpty()) return Optional.empty();
        return Optional.of(apply(QualifiedName.ROOT, (qn, e)->e.type==ElementType.PART_PATH ? qn.add((((NamedElement)e).name)) : qn));
    }

}

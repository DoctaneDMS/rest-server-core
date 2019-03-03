package com.softwareplumbers.dms.rest.server.model;
import com.softwareplumbers.common.QualifiedName;

public interface DocumentPart extends Document {
    QualifiedName getPartName();
}

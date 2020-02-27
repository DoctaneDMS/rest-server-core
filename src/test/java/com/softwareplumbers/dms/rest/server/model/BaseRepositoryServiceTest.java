package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Exceptions.*;

import static org.junit.Assert.*;

import javax.json.JsonObject;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Constants;

import static com.softwareplumbers.dms.Constants.*;
import com.softwareplumbers.dms.common.test.DocumentServiceTest;
import static com.softwareplumbers.dms.common.test.TestUtils.*;


/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 * Note that the underlying repository must support at least the metadata field "Description" in both workspaces and
 * documents. This is important in implementations for systems such as Filenet which have fixed schemas for content
 * metadata.
 * 
 */
public abstract class BaseRepositoryServiceTest extends DocumentServiceTest {
			
    /** BEGIN -- Tests ServerSide only */
    @Test 
	public void testWorkspaceCopyById() throws BaseException {
        JsonObject metadata = workspaceMetadataModel().generateValue();
		Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), Workspace.State.Open , metadata, Options.CREATE_MISSING_PARENT);
        QualifiedName newName = randomQualifiedName();
        service().copyWorkspace(workspace.getId(), QualifiedName.ROOT, Constants.ROOT_ID, newName, true);
        Workspace result = service().getWorkspaceByName(ROOT_ID, newName);
        assertEquals(metadata, result.getMetadata());
	}
    
    @Test(expected = InvalidWorkspace.class)
	public void testWorkspaceCopyByIdWithNewPathOriginalMustExist() throws BaseException {
        service().copyWorkspace(randomWorkspaceId(), QualifiedName.ROOT, Constants.ROOT_ID, randomQualifiedName(), true);
	}
    
    /** END -- Tests ServerSide only */
       
    
    
    

}

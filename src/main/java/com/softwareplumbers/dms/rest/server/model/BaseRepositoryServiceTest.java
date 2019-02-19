package com.softwareplumbers.dms.rest.server.model;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.MediaType;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;

/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 */
public abstract class BaseRepositoryServiceTest {
	
	public abstract RepositoryService service();
	
	public static final String[] names = { "julien", "peter", "fairfax", "austen", "celtic", "a", "the", "halibut", "eaten" };
	public static final String characters = "-._~";
	public static final String reserved = "&$+,/:;=?@#";
	
	public static int unique = 0;
	
	public static final String randomUrlSafeName() {
		StringBuffer buffer = new StringBuffer(names[(int)(Math.random() * names.length)]);
		buffer.append(characters.charAt((int)(Math.random() * characters.length())));
		buffer.append(Integer.toHexString(unique++));
		return buffer.toString();
	}
	
	public static final String randomReservedName() {
		StringBuffer buffer = new StringBuffer();
		for (int i = 1; i < 3; i++) {
			buffer.append(reserved.charAt((int)(Math.random() * reserved.length())));
			buffer.append(randomUrlSafeName());
		}
		return buffer.toString();
	}
	
	public static final String randomText() {
		StringBuffer buffer = new StringBuffer();
		for (int i = 1; i < 10; i++) {
			buffer.append(randomUrlSafeName());
			buffer.append(" ");
		}
		return buffer.toString();		
	}
	
	public static final InputStream toStream(String out) {
		return new ByteArrayInputStream(out.getBytes());
	}
	
	public abstract Reference randomDocumentReference();
	public abstract String randomWorkspaceId();
	
	@Test
	public void testAnonymousCreateWorkspace() throws InvalidWorkspace {
		String workspace1 = service().createWorkspace(null, State.Open);
		assertNotNull(workspace1);
		String workspace2 = service().createWorkspace(null, State.Open);
		assertNotNull(workspace1);
		assertNotEquals(workspace1, workspace2);
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithURLSafeName() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		String workspace = service().createWorkspace(name, State.Open);
		Workspace ws = service().getWorkspaceByName(name);
		assertEquals(workspace, ws.getId());
	}
	
	@Test(expected = InvalidReference.class)
	public void testRepositoryFetchWithInvalidRef() throws IOException, InvalidReference {
		Reference ref1 = randomDocumentReference();
		service().getDocument(ref1);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testGetWorkspaceNotFoundByNameError() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace test = service().getWorkspaceByName(name);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testGetWorkspaceNotFoundByIdError() throws InvalidWorkspace {
		Workspace test = service().getWorkspaceById(randomWorkspaceId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testUpdateWorkspaceNotFoundError() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceByName(name, null, Workspace.State.Closed, false);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testCreateDocumentNotFoundError() throws InvalidWorkspace, InvalidWorkspaceState, IOException {
		service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), 
			null,
			randomWorkspaceId(), 
			false);
	}
	
	@Test (expected = InvalidDocumentId.class)
	public void testDeleteDocumentInvalidDocumentError() throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		String workspace = service().createWorkspace(null, State.Open);
		service().deleteDocument(workspace, randomDocumentReference().getId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testDeleteDocumentInvalidWorkspaceId() throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		Reference ref = service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), null, null, false
		);
		service().deleteDocument(randomWorkspaceId(), ref.id);
	}
	
	@Test 
	public void testListWorkspacesInvalidWorkspaceId() throws InvalidDocumentId {
		assertEquals(0L,service().listWorkspaces(randomDocumentReference().id).count());
	}

	@Test
	public void testContentLength() throws InvalidReference, InvalidWorkspace, InvalidWorkspaceState {
		for (int i = 0; i < 3; i++) {
			String testData = randomText();
			Reference ref = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(testData), null, null, false);
			Document doc = service().getDocument(ref);
			assertEquals(testData.length(), doc.getLength());
		}
	}
	
	@Test
	public void testRenameWorkspace() throws InvalidWorkspace {
		String wsId = service().createWorkspace(null, State.Open);
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceById(wsId, wsName, null, true);
		Workspace ws = service().getWorkspaceByName(wsName);
		assertEquals(wsId, ws.getId());
	}
	
	@Test  (expected = InvalidWorkspace.class)
	public void testRenameFolderExistingName() throws InvalidWorkspace {
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().createWorkspace(wsName, State.Open);
		String wsId = service().createWorkspace(null, State.Open);
		service().updateWorkspaceById(wsId,wsName, null, false);
	}
	
	@Test
	public void testSearchForWorkspaceByWildcard() throws InvalidWorkspace {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		QualifiedName peter_jones = jones.add("peter");
		QualifiedName peter_carter = carter.add("peter");
		QualifiedName pamela_jones = jones.add("pamela");
		QualifiedName roger_carter = carter.add("roger");
		service().createWorkspace(peter_jones, State.Open);
		service().createWorkspace(peter_carter, State.Open);
		service().createWorkspace(pamela_jones, State.Open);
		service().createWorkspace(roger_carter, State.Open);
				
		assertEquals(4, service().catalogueByName(base.addAll("*","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(base.addAll("jones","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(base.addAll("carter","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(base.addAll("*","p*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(base.addAll("*","*r"), ObjectConstraint.UNBOUNDED, false).count());
	}
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms;

import com.softwareplumbers.common.QualifiedName;

/**
 *
 * @author SWPNET\jonessex
 */
public interface Exceptions {
   //-------------- Exception classes --------------//
    
    public static class BaseException extends Exception {
        public BaseException(String description) { super(description); }
    }
    
    public static class BaseRuntimeException extends RuntimeException {
        public BaseRuntimeException(BaseException e) { super(e); }
    }

	/** Exception type for an invalid document reference */
	public static class InvalidReference extends BaseException {
		private static final long serialVersionUID = 4890221706381667729L;
		public final Reference reference;
		public InvalidReference(Reference reference) {
			super("Invalid reference: " + reference);
			this.reference = reference;
		}
	}

	/** Exception type for an invalid document id */
	public static class InvalidDocumentId extends BaseException {
		private static final long serialVersionUID = 4890221706381667729L;
		public final String id;
		public InvalidDocumentId(String id) {
			super("Invalid document id: " + id);
			this.id = id;
		}
	}

	/** Exception type for an invalid workspace name or id */
	public static class InvalidWorkspace extends BaseException {
		private static final long serialVersionUID = 2546274609900213587L;
		public final String rootId;
        public final QualifiedName workspace;
		public InvalidWorkspace(String rootId, QualifiedName workspace) {
			super("Invalid workspace name: " + rootId + ":" + workspace);
			this.workspace = workspace;
            this.rootId = rootId;
		}
        public InvalidWorkspace(String rootId) {
            this(rootId, QualifiedName.ROOT);
        }
	}
	
	/** Exception type for an invalid name (for either document or workspace) */
	public static class InvalidObjectName extends BaseException {

		private static final long serialVersionUID = 7176066099090799797L;
        public final String rootId;
		public final QualifiedName name;
		public InvalidObjectName(String rootId, QualifiedName name) {
			super("Invalid name: " + rootId + ":" + name);
			this.name = name;
            this.rootId = rootId;
		}
	}

	/** Exception type for an invalid workspace state */
	public static class InvalidWorkspaceState extends BaseException {
		private static final long serialVersionUID = -4516622808487331082L;
		public final String workspace;
		public final Workspace.State state;
		public final String other;
		public InvalidWorkspaceState(QualifiedName workspace, Workspace.State state) {
			super("Attempt to change workspace: " + workspace + " in state " + state);
			this.workspace = workspace.toString();
			this.state = state;
			this.other = null;
		}
		public InvalidWorkspaceState(String workspace, Workspace.State state) {
			super("Attempt to change workspace: " + workspace + " in state " + state);
			this.workspace = workspace;
			this.state = state;
			this.other = null;
		}
		public InvalidWorkspaceState(String workspace, String other) {
			super("Workspace: " + workspace + " invalid state: " + other);
			this.workspace = workspace;
			this.state = null;
			this.other = other;
			
		}
	}
    
}

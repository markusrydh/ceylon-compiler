package com.redhat.ceylon.compiler.java.test;

import javax.tools.Diagnostic;

public class CompilerError implements Comparable<CompilerError>{
    public enum Classification {
        /** A Ceylon syntax or typechecker error */
        FRONTEND,
        /** A javac typechecker error */
        BACKEND,
        /** A propogated exception */
        CRASH,
        /** An unclassified error */
        UNCLASSIFIED
    }
    public final long lineNumber;
    public final String message;
    public final Diagnostic.Kind kind;
    public final String filename; // note: not included in equals(),hash(),compareTo()
    public final Classification classification; // note: not included in equals(),hash(),compareTo()

    public CompilerError(long lineNumber, String message) {
        this(Diagnostic.Kind.ERROR, null, lineNumber, message);
    }
    
    public CompilerError(Diagnostic.Kind kind, String filename, long lineNumber, String message) {
        this(kind, filename, lineNumber, message, Classification.UNCLASSIFIED);
    }
    
    public CompilerError(Diagnostic.Kind kind, String filename, long lineNumber, String message, Classification classification) {
        this.kind = kind;
        this.filename = filename;
        this.lineNumber = lineNumber;
        this.message = message;
        this.classification = classification;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((kind == null) ? 0 : kind.hashCode());
        result = prime * result + (int) (lineNumber ^ (lineNumber >>> 32));
        result = prime * result
                + ((message == null) ? 0 : message.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CompilerError other = (CompilerError) obj;
        if (kind != other.kind)
            return false;
        if (lineNumber != other.lineNumber)
            return false;
        if (message == null) {
            if (other.message != null)
                return false;
        } else if (!message.equals(other.message))
            return false;
        return true;
    }

    public String toString() {
        String sort;
        switch (classification) {
        case BACKEND:
            sort = "backend";
            break;
        case FRONTEND:
            sort = "frontend";
            break;
        case CRASH:
            sort = "crash";
            break;
        case UNCLASSIFIED:
        default:
            sort = "unclassified";
            break;
        }
        return kind + ": " + sort + ": " +  lineNumber + ": " + message;
    }

    @Override
    public int compareTo(CompilerError o) {
        long cmp = this.kind.compareTo(o.kind);
        if (cmp == 0) {
            cmp = this.lineNumber - o.lineNumber;
        }
        if (cmp == 0) {
            cmp = this.message.compareTo(o.message);
        }
        return Long.signum(cmp);
    }
}
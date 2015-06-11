package de.derivo.sparqldlapi;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 11/06/15
 */
public class Var {

    private String name;

    public Var(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Var)) {
            return false;
        }
        Var other = (Var) obj;
        return this.name.equals(other.name);
    }
}

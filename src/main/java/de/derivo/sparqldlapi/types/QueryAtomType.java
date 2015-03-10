// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.types;

/**
 * All possible query atoms types.
 *
 * @author Mario Volke
 */
public enum QueryAtomType {
    CLASS("Class"), PROPERTY("Property"), INDIVIDUAL("Individual"),
    TYPE("Type"), PROPERTY_VALUE("PropertyValue"), EQUIVALENT_CLASS("EquivalentClass"),
    SUB_CLASS_OF("SubClassOf"), EQUIVALENT_PROPERTY("EquivalentProperty"),
    SUB_PROPERTY_OF("SubPropertyOf"), INVERSE_OF("InverseOf"),
    OBJECT_PROPERTY("ObjectProperty"), DATA_PROPERTY("DataProperty"), ANNOTATION_PROPERTY("AnnotationProperty"),
    FUNCTIONAL("Functional"), INVERSE_FUNCTIONAL("InverseFunctional"),
    TRANSITIVE("Transitive"), SYMMETRIC("Symmetric"), IRREFLEXIVE("Irreflexive"), REFLEXIVE("Reflexive"),
    SAME_AS("SameAs"), DISJOINT_WITH("DisjointWith"), DIFFERENT_FROM("DifferentFrom"),
    COMPLEMENT_OF("ComplementOf"), ANNOTATION("Annotation"),

    /* class/property-hierarchy extension */
    STRICT_SUB_CLASS_OF("StrictSubClassOf"), DIRECT_SUB_CLASS_OF("DirectSubClassOf"),
    DIRECT_TYPE("DirectType"),
    STRICT_SUB_PROPERTY_OF("StrictSubPropertyOf"), DIRECT_SUB_PROPERTY_OF("DirectSubPropertyOf"),

    UKNOWN;

    private final String syntax;

    private QueryAtomType() {
        this(null);
    }

    private QueryAtomType(String syntax) {
        this.syntax = syntax;
    }

    public static QueryAtomType fromString(String str) {
        for (QueryAtomType value : values()) {
            if (value.syntax != null && value.syntax.equalsIgnoreCase(str)) {
                return value;
            }
        }

        return QueryAtomType.UKNOWN;
    }

    public String toString() {
        return syntax;
    }
}

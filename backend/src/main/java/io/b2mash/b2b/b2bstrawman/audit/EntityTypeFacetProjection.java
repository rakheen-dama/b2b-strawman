package io.b2mash.b2b.b2bstrawman.audit;

/** Spring Data projection for entity-type-facet aggregate rows. */
public interface EntityTypeFacetProjection {

  String getEntityType();

  long getCount();
}

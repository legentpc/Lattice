package com.moulberry.lattice.annotation.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LatticeWidgetDraggableList {

    boolean allowDeleting() default true;
    boolean requireNonEmpty() default false;

}

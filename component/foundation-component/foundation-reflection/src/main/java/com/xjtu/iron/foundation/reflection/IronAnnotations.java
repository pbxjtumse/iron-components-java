package com.xjtu.iron.foundation.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

/**
 * 注解读取工具薄封装。
 */
public final class IronAnnotations {

    private IronAnnotations() {}

    public static <A extends Annotation> Optional<A> findAnnotation(AnnotatedElement element, Class<A> annotationType) {
        if (element == null || annotationType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(element.getAnnotation(annotationType));
    }

    public static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return findAnnotation(element, annotationType).isPresent();
    }
}

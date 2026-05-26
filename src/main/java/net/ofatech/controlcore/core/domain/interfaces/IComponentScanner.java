package net.ofatech.controlcore.core.domain.interfaces;

import java.util.Set;

public interface IComponentScanner {
    Set<Class<?>> scan(String basePackage, ClassLoader classLoader);
}

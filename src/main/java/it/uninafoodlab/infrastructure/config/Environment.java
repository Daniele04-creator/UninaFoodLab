package it.uninafoodlab.infrastructure.config;

@FunctionalInterface
public interface Environment {

    String get(String name);
}

package it.uninafoodlab.infrastructure.config;

public enum SystemEnvironment implements Environment {
    INSTANCE;

    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}

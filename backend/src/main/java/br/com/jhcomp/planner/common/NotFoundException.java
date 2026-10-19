package br.com.jhcomp.planner.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String resource, Object id) {
        super(resource + " não encontrado: " + id);
    }
}

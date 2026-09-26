package org.camelia.studio.kiss.shot.acerola.models;

import java.util.List;

/**
 * Résultat de {@code POST /servers/sync} : serveurs enregistrés et serveurs passés en {@code LEFT}.
 */
public record ServerSynchronization(int registered, List<String> left) {
}

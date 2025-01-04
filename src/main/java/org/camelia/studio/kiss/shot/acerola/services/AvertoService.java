package org.camelia.studio.kiss.shot.acerola.services;

import java.util.List;

import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.camelia.studio.kiss.shot.acerola.repositories.AvertoRepository;

public class AvertoService {
    private static AvertoService instance;

    public static AvertoService getInstance() {
        if (instance == null) {
            instance = new AvertoService();
        }

        return instance;
    }

    public List<Averto> getLatestAvertos(int amount) {
        return AvertoRepository.getInstance().findCount(amount);
    }

}

package org.camelia.studio.kiss.shot.acerola.services;

import org.camelia.studio.kiss.shot.acerola.models.*;
import org.camelia.studio.kiss.shot.acerola.repositories.UserRepository;

import java.util.List;

public class UserService {
    private static UserService instance;

    public static UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }

        return instance;
    }

    public User getOrCreateUser(String discordId) {
        User user = UserRepository.getInstance().findByDiscordId(discordId);

        if (user == null) {
            user = new User(discordId);
            UserRepository.getInstance().save(user);
        }

        return user;
    }

    public List<User> getAllUsers() {
        return UserRepository.getInstance().findAll();
    }

    public void updateUser(User user) {
        UserRepository.getInstance().update(user);
    }
}

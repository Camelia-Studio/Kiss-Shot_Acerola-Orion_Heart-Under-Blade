package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.*;

import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
public class User implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<Averto> avertos;

    @OneToMany(mappedBy = "moderator", fetch = FetchType.EAGER)
    private List<Averto> moderatedAvertos;

    @Column(name = "discordId", nullable = false, unique = true)
    private String discordId;

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(String discordId) {
        this.discordId = discordId;
    }

    public Long getId() {
        return id;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Averto> getAvertos() {
        return avertos;
    }

    public List<Averto> getModeratedAvertos() {
        return moderatedAvertos;
    }
}
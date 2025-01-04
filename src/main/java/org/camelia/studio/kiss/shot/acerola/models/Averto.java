package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.*;

import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "avertos")
public class Averto implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    private User moderator;

    @Column(name = "reason", nullable = true, unique = false)
    private String reason;

    @Column(name = "file", nullable = true, unique = false)
    private String file;

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public Averto() {
    }

    public Averto(User user, User moderator) {
        this.user = user;
        this.moderator = moderator;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getReason() {
        return reason;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public User getModerator() {
        return moderator;
    }

    public User getUser() {
        return user;
    }
}
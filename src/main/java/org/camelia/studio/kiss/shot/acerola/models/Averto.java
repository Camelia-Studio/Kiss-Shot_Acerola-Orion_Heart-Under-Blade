package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.*;

import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private DiscordServer server;

    @Column(name = "reason", nullable = true, unique = false)
    private String reason;

    @Column(name = "file", nullable = true, unique = false)
    private String file;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "createdAt")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updatedAt")
    private Instant updatedAt;

    public Averto() {
    }

    public Averto(User user, User moderator, DiscordServer server) {
        this.user = user;
        this.moderator = moderator;
        this.server = server;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
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

    public DiscordServer getServer() {
        return server;
    }
}

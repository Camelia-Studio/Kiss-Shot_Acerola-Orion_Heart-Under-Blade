package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "server_modules",
        uniqueConstraints = @UniqueConstraint(name = "uk_server_modules_server_module", columnNames = {"server_id", "module"}))
public class ServerModule implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private DiscordServer server;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 32)
    private ModuleType module;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ModuleStatus status;

    @Column(name = "suspension_reason", length = 255)
    private String suspensionReason;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ServerModule() {
    }

    public ServerModule(DiscordServer server, ModuleType module) {
        this.server = server;
        this.module = module;
        this.status = ModuleStatus.DISABLED;
    }

    public ModuleType getModule() {
        return module;
    }

    public Long getId() {
        return id;
    }

    public DiscordServer getServer() {
        return server;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public void activate() {
        status = ModuleStatus.ACTIVE;
        suspensionReason = null;
    }

    public void disable() {
        status = ModuleStatus.DISABLED;
        suspensionReason = null;
    }

    public void suspend(String reason) {
        status = ModuleStatus.SUSPENDED;
        suspensionReason = reason;
    }
}

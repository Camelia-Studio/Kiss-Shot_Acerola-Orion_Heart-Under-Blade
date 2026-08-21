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
import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "configuration_history")
public class ConfigurationHistory implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private DiscordServer server;

    @Column(name = "actor_id", nullable = false, length = 32)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 32)
    private ModuleType module;

    @Column(name = "setting", nullable = false, length = 64)
    private String setting;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConfigurationHistory() {
    }

    public ConfigurationHistory(
            DiscordServer server,
            String actorId,
            ModuleType module,
            String setting,
            String oldValue,
            String newValue
    ) {
        this.server = server;
        this.actorId = actorId;
        this.module = module;
        this.setting = setting;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}

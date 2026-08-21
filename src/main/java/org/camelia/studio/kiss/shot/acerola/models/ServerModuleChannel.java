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

@Entity
@Table(
        name = "server_module_channels",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_server_module_channels_resource",
                columnNames = {"server_module_id", "purpose", "channel_id"}))
public class ServerModuleChannel implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_module_id", nullable = false)
    private ServerModule serverModule;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private ModuleResourcePurpose purpose;

    @Column(name = "channel_id", nullable = false, length = 20)
    private String channelId;

    public ServerModuleChannel() {
    }

    public ServerModuleChannel(ServerModule serverModule, ModuleResourcePurpose purpose, String channelId) {
        this.serverModule = serverModule;
        this.purpose = purpose;
        this.channelId = channelId;
    }
}

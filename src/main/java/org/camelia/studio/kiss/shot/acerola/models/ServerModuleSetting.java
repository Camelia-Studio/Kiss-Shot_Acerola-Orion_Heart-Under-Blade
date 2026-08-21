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
        name = "server_module_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_server_module_settings_key",
                columnNames = {"server_module_id", "setting"}))
public class ServerModuleSetting implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_module_id", nullable = false)
    private ServerModule serverModule;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting", nullable = false, length = 64)
    private ModuleSetting setting;

    @Column(name = "value", nullable = false, columnDefinition = "text")
    private String value;

    public ServerModuleSetting() {
    }

    public ServerModuleSetting(ServerModule serverModule, ModuleSetting setting, String value) {
        this.serverModule = serverModule;
        this.setting = setting;
        this.value = value;
    }
}

package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "server_protected_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_server_protected_roles_resource",
                columnNames = {"server_id", "role_id"}))
public class ServerProtectedRole implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private DiscordServer server;

    @Column(name = "role_id", nullable = false, length = 20)
    private String roleId;

    public ServerProtectedRole() {
    }

    public ServerProtectedRole(DiscordServer server, String roleId) {
        this.server = server;
        this.roleId = roleId;
    }
}

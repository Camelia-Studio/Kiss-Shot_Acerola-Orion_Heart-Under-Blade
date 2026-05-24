package org.camelia.studio.kiss.shot.acerola.models;

import jakarta.persistence.*;
import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.camelia.studio.kiss.shot.acerola.utils.StringListConverter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "leave_surveys")
public class
LeaveSurvey implements IEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discordId", nullable = false)
    private String discordId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "guildId", nullable = false)
    private String guildId;

    @Column(name = "joinedAt", nullable = false)
    private Instant joinedAt;

    @Column(name = "leftAt", nullable = false)
    private Instant leftAt;

    @Column(name = "response", length = 500)
    private String response;

    @Column(name = "responded", nullable = false)
    private boolean responded = false;

    @Convert(converter = StringListConverter.class)
    @Column(name = "buttonLabels", columnDefinition = "TEXT")
    private List<String> buttonLabels = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    public LeaveSurvey() {}

    public LeaveSurvey(String discordId, String username, String guildId, Instant joinedAt, Instant leftAt) {
        this.discordId = discordId;
        this.username = username;
        this.guildId = guildId;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public Long getId() { return id; }
    public String getDiscordId() { return discordId; }
    public String getUsername() { return username; }
    public String getGuildId() { return guildId; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }
    public String getResponse() { return response; }
    public boolean isResponded() { return responded; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<String> getButtonLabels() { return buttonLabels; }

    public void setResponse(String response) { this.response = response; }
    public void setResponded(boolean responded) { this.responded = responded; }
    public void setButtonLabels(List<String> buttonLabels) { this.buttonLabels = buttonLabels; }
}

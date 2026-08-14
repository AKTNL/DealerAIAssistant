package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportSubscriptionSchedule;
import com.brand.agentpoc.reporting.domain.ReportType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "report_subscriptions",
        indexes = {
            @Index(name = "idx_report_subscriptions_owner", columnList = "tenant_id,creator_user_id,deleted_at,id"),
            @Index(name = "idx_report_subscriptions_due", columnList = "tenant_id,enabled,next_run_at,id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_report_subscriptions_active_definition",
                columnNames = {"tenant_id", "creator_user_id", "active_configuration_key"}
        )
)
public class ReportSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "report_type", nullable = false, length = 16)
    private String reportType;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_id", nullable = false, length = 2048)
    private String scopeId;

    @Column(nullable = false, length = 8)
    private String language;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(name = "schedule_kind", nullable = false, length = 16)
    private String scheduleKind;

    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "channel_key", nullable = false, length = 32)
    private String channelKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "report_subscription_recipients",
            joinColumns = @JoinColumn(name = "subscription_id", nullable = false)
    )
    @Column(name = "recipient_user_id", nullable = false)
    private Set<Long> recipientUserIds;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "misfire_policy", nullable = false, length = 16)
    private String misfirePolicy;

    @Column(name = "misfire_grace_minutes", nullable = false)
    private Integer misfireGraceMinutes;

    @Column(name = "active_configuration_key", length = 64)
    private String activeConfigurationKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReportSubscriptionEntity() {
        recipientUserIds = new LinkedHashSet<>();
    }

    public ReportSubscriptionEntity(
            Long tenantId,
            Long creatorUserId,
            ReportType reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportSubscriptionSchedule schedule,
            String channelKey,
            Set<Long> recipientUserIds,
            boolean enabled,
            Instant nextRunAt,
            String misfirePolicy,
            int misfireGraceMinutes,
            String activeConfigurationKey,
            Instant now
    ) {
        this.tenantId = tenantId;
        this.creatorUserId = creatorUserId;
        this.recipientUserIds = new LinkedHashSet<>();
        applyDefinition(reportType, scope, language, topic, schedule, channelKey,
                recipientUserIds, activeConfigurationKey);
        this.enabled = enabled;
        this.nextRunAt = nextRunAt;
        this.misfirePolicy = misfirePolicy;
        this.misfireGraceMinutes = misfireGraceMinutes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDefinition(
            ReportType reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportSubscriptionSchedule schedule,
            String channelKey,
            Set<Long> recipientUserIds,
            String activeConfigurationKey,
            Instant nextRunAt,
            Instant now
    ) {
        applyDefinition(reportType, scope, language, topic, schedule, channelKey,
                recipientUserIds, activeConfigurationKey);
        this.nextRunAt = nextRunAt;
        this.updatedAt = now;
    }

    public void changeEnabled(boolean enabled, Instant nextRunAt, Instant now) {
        this.enabled = enabled;
        this.nextRunAt = enabled ? nextRunAt : null;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        enabled = false;
        nextRunAt = null;
        activeConfigurationKey = null;
        deletedAt = now;
        updatedAt = now;
    }

    public ReportType reportType() {
        return ReportType.parse(reportType);
    }

    public ReportScope scope() {
        return new ReportScope(scopeType, scopeId);
    }

    public ReportSubscriptionSchedule schedule() {
        return new ReportSubscriptionSchedule(
                ReportSubscriptionSchedule.Kind.valueOf(scheduleKind),
                localTime,
                ZoneId.of(timeZone),
                dayOfWeek == null ? null : DayOfWeek.of(dayOfWeek),
                dayOfMonth
        );
    }

    private void applyDefinition(
            ReportType reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportSubscriptionSchedule schedule,
            String channelKey,
            Set<Long> recipientUserIds,
            String activeConfigurationKey
    ) {
        this.reportType = reportType.wireName();
        this.scopeType = scope.type();
        this.scopeId = scope.id();
        this.language = language;
        this.topic = topic;
        this.scheduleKind = schedule.kind().name();
        this.localTime = schedule.localTime();
        this.timeZone = schedule.timeZone().getId();
        this.dayOfWeek = schedule.dayOfWeek() == null ? null : schedule.dayOfWeek().getValue();
        this.dayOfMonth = schedule.dayOfMonth();
        this.channelKey = channelKey;
        this.recipientUserIds = new LinkedHashSet<>(recipientUserIds);
        this.activeConfigurationKey = activeConfigurationKey;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getCreatorUserId() { return creatorUserId; }
    public String getLanguage() { return language; }
    public String getTopic() { return topic; }
    public String getChannelKey() { return channelKey; }
    public Set<Long> getRecipientUserIds() { return Set.copyOf(recipientUserIds); }
    public Boolean getEnabled() { return enabled; }
    public Instant getNextRunAt() { return nextRunAt; }
    public String getMisfirePolicy() { return misfirePolicy; }
    public Integer getMisfireGraceMinutes() { return misfireGraceMinutes; }
    public String getActiveConfigurationKey() { return activeConfigurationKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getVersion() { return version; }
}

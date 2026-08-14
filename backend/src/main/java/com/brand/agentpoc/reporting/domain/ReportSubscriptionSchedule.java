package com.brand.agentpoc.reporting.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ReportSubscriptionSchedule(
        Kind kind,
        LocalTime localTime,
        ZoneId timeZone,
        DayOfWeek dayOfWeek,
        Integer dayOfMonth
) {

    private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();
    private static final String LOCAL_TIME_PATTERN = "(?:[01]\\d|2[0-3]):[0-5]\\d";

    public ReportSubscriptionSchedule {
        if (kind == null || localTime == null || timeZone == null) {
            throw new IllegalArgumentException("Schedule kind, local time, and time zone are required.");
        }
        if (!AVAILABLE_ZONE_IDS.contains(timeZone.getId())) {
            throw new IllegalArgumentException("timeZone must be a valid IANA time zone.");
        }
        switch (kind) {
            case DAILY -> requireNoCalendarSelector(dayOfWeek, dayOfMonth);
            case WEEKLY -> {
                if (dayOfWeek == null || dayOfMonth != null) {
                    throw new IllegalArgumentException("WEEKLY schedules require only dayOfWeek.");
                }
            }
            case MONTHLY -> {
                if (dayOfWeek != null || dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
                    throw new IllegalArgumentException("MONTHLY schedules require dayOfMonth from 1 to 28.");
                }
            }
        }
    }

    public static ReportSubscriptionSchedule parse(
            String kind,
            String localTime,
            String timeZone,
            Integer dayOfWeek,
            Integer dayOfMonth
    ) {
        Kind parsedKind = Kind.parse(kind);
        LocalTime parsedTime = parseLocalTime(localTime);
        ZoneId parsedZone = parseTimeZone(timeZone);
        DayOfWeek parsedDay = dayOfWeek == null ? null : parseDayOfWeek(dayOfWeek);
        return new ReportSubscriptionSchedule(parsedKind, parsedTime, parsedZone, parsedDay, dayOfMonth);
    }

    public Instant nextAfter(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Reference time is required.");
        }
        ZonedDateTime reference = instant.atZone(timeZone);
        LocalDate candidateDate = initialDate(reference.toLocalDate());
        ZonedDateTime candidate = resolve(candidateDate);
        if (!candidate.toInstant().isAfter(instant)) {
            candidate = resolve(advance(candidateDate));
        }
        return candidate.toInstant();
    }

    public String canonicalValue() {
        return String.join("|",
                kind.name(),
                localTime.toString(),
                timeZone.getId(),
                dayOfWeek == null ? "" : String.valueOf(dayOfWeek.getValue()),
                dayOfMonth == null ? "" : String.valueOf(dayOfMonth));
    }

    private LocalDate initialDate(LocalDate referenceDate) {
        return switch (kind) {
            case DAILY -> referenceDate;
            case WEEKLY -> referenceDate.plusDays(
                    Math.floorMod(dayOfWeek.getValue() - referenceDate.getDayOfWeek().getValue(), 7));
            case MONTHLY -> YearMonth.from(referenceDate).atDay(dayOfMonth);
        };
    }

    private LocalDate advance(LocalDate candidateDate) {
        return switch (kind) {
            case DAILY -> candidateDate.plusDays(1);
            case WEEKLY -> candidateDate.plusWeeks(1);
            case MONTHLY -> YearMonth.from(candidateDate).plusMonths(1).atDay(dayOfMonth);
        };
    }

    private ZonedDateTime resolve(LocalDate date) {
        LocalDateTime localDateTime = LocalDateTime.of(date, localTime);
        ZoneRules rules = timeZone.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(localDateTime);
        if (!validOffsets.isEmpty()) {
            return ZonedDateTime.ofLocal(localDateTime, timeZone, validOffsets.getFirst());
        }
        ZoneOffsetTransition transition = rules.getTransition(localDateTime);
        if (transition == null) {
            throw new IllegalStateException("Unable to resolve schedule time zone transition.");
        }
        return ZonedDateTime.ofLocal(transition.getDateTimeAfter(), timeZone, transition.getOffsetAfter());
    }

    private static void requireNoCalendarSelector(DayOfWeek dayOfWeek, Integer dayOfMonth) {
        if (dayOfWeek != null || dayOfMonth != null) {
            throw new IllegalArgumentException("DAILY schedules do not accept day selectors.");
        }
    }

    private static LocalTime parseLocalTime(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches(LOCAL_TIME_PATTERN)) {
            throw new IllegalArgumentException("localTime must use HH:mm in 24-hour time.");
        }
        return LocalTime.parse(normalized);
    }

    private static ZoneId parseTimeZone(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!AVAILABLE_ZONE_IDS.contains(normalized)) {
            throw new IllegalArgumentException("timeZone must be a valid IANA time zone.");
        }
        return ZoneId.of(normalized);
    }

    private static DayOfWeek parseDayOfWeek(Integer value) {
        if (value < 1 || value > 7) {
            throw new IllegalArgumentException("dayOfWeek must be from 1 (Monday) to 7 (Sunday).");
        }
        return DayOfWeek.of(value);
    }

    public enum Kind {
        DAILY,
        WEEKLY,
        MONTHLY;

        public static Kind parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("scheduleKind is required.");
            }
            try {
                return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "scheduleKind must be DAILY, WEEKLY, or MONTHLY.", exception);
            }
        }
    }
}

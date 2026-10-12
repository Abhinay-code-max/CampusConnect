package com.campusconnect.model;

/** Full event representation accepted by create and edit endpoints. */
public final class EventRequest {
    private String eventCode;
    private String eventName;
    private String description;
    private String participationMode;
    private String registrationOpens;
    private String registrationCloses;
    private String startsAt;
    private String endsAt;
    private Integer capacity;
    private String location;
    public String getEventCode() { return eventCode; }
    public void setEventCode(String value) { eventCode = value; }
    public String getEventName() { return eventName; }
    public void setEventName(String value) { eventName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getParticipationMode() { return participationMode; }
    public void setParticipationMode(String value) { participationMode = value; }
    public String getRegistrationOpens() { return registrationOpens; }
    public void setRegistrationOpens(String value) { registrationOpens = value; }
    public String getRegistrationCloses() { return registrationCloses; }
    public void setRegistrationCloses(String value) { registrationCloses = value; }
    public String getStartsAt() { return startsAt; }
    public void setStartsAt(String value) { startsAt = value; }
    public String getEndsAt() { return endsAt; }
    public void setEndsAt(String value) { endsAt = value; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer value) { capacity = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
}

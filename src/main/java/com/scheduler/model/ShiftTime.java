package com.scheduler.model;

import java.time.LocalTime;

public class ShiftTime {
    private final LocalTime startTime;
    private final LocalTime endTime;

    public ShiftTime(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}

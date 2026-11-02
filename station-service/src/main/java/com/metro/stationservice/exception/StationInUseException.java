package com.metro.stationservice.exception;

public class StationInUseException extends RuntimeException {
	public StationInUseException(Long id) {
		super("Station " + id + " is used by one or more segments. Delete those segments first.");
	}
}

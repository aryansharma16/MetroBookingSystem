package com.metro.stationservice.exception;

public class LineInUseException extends RuntimeException {
	public LineInUseException(Long id) {
		super("Line " + id + " is used by one or more segments. Delete those segments first.");
	}
}

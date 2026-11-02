package com.metro.stationservice.exception;

public class SegmentNotFoundException extends RuntimeException {
	public SegmentNotFoundException(Long id) {
		super("Segment not found with id: " + id);
	}
}

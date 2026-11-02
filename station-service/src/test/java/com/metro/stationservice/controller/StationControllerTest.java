package com.metro.stationservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.metro.stationservice.exception.StationInUseException;
import com.metro.stationservice.exception.StationNotFoundException;
import com.metro.stationservice.repository.SegmentRepository;
import com.metro.stationservice.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class StationControllerTest {

	@Mock
	private StationRepository stationRepository;

	@Mock
	private SegmentRepository segmentRepository;

	@InjectMocks
	private StationController controller;

	@Test
	void deleteRejectsStationUsedBySegment() {
		when(stationRepository.existsById(1L)).thenReturn(true);
		when(segmentRepository.existsByStationAIdOrStationBId(1L, 1L)).thenReturn(true);

		try {
			controller.removeStation(1L);
			fail("Expected StationInUseException");
		} catch (StationInUseException e) {
			assertEquals("Station 1 is used by one or more segments. Delete those segments first.", e.getMessage());
		}
		verify(stationRepository, never()).deleteById(anyLong());
	}

	@Test
	void deleteMissingStationIsNotFound() {
		when(stationRepository.existsById(7L)).thenReturn(false);

		try {
			controller.removeStation(7L);
			fail("Expected StationNotFoundException");
		} catch (StationNotFoundException e) {
			assertEquals("Station not found with id: 7", e.getMessage());
		}
		verify(stationRepository, never()).deleteById(anyLong());
	}

	@Test
	void deleteUnusedStationRemovesIt() {
		when(stationRepository.existsById(2L)).thenReturn(true);
		when(segmentRepository.existsByStationAIdOrStationBId(2L, 2L)).thenReturn(false);

		controller.removeStation(2L);

		verify(stationRepository).deleteById(2L);
	}
}

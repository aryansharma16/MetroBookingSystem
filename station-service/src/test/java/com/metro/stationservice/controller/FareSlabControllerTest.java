package com.metro.stationservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.metro.stationservice.dto.request.FareSlabCreateDto;
import com.metro.stationservice.dto.response.FareSlabDto;
import com.metro.stationservice.entity.FareSlab;
import com.metro.stationservice.exception.InvalidFareSlabException;
import com.metro.stationservice.repository.FareSlabRepository;

@ExtendWith(MockitoExtension.class)
class FareSlabControllerTest {

	@Mock
	private FareSlabRepository fareSlabRepository;

	@InjectMocks
	private FareSlabController controller;

	@Test
	void addRejectsSlabOverlappingExistingSlab() {
		when(fareSlabRepository.findAll()).thenReturn(List.of(slab(1L, 0, 5)));

		try {
			controller.addFareSlab(request(3, 8));
			fail("Expected InvalidFareSlabException");
		} catch (InvalidFareSlabException e) {
			assertEquals("Fare slab overlaps with existing slab 0.0-5.0 km", e.getMessage());
		}
		verify(fareSlabRepository, never()).save(any(FareSlab.class));
	}

	@Test
	void addAllowsTouchingEdges() {
		when(fareSlabRepository.findAll()).thenReturn(List.of(slab(1L, 0, 5)));
		when(fareSlabRepository.save(any(FareSlab.class))).thenReturn(slab(2L, 5, 10));

		FareSlabDto dto = controller.addFareSlab(request(5, 10));

		assertEquals(2L, dto.getId());
		assertEquals(5.0, dto.getMinDistanceKm());
		assertEquals(10.0, dto.getMaxDistanceKm());
	}

	@Test
	void addRejectsMinNotLessThanMax() {
		try {
			controller.addFareSlab(request(5, 5));
			fail("Expected InvalidFareSlabException");
		} catch (InvalidFareSlabException e) {
			assertEquals("Minimum distance must be less than maximum distance", e.getMessage());
		}
		verify(fareSlabRepository, never()).save(any(FareSlab.class));
	}

	@Test
	void updateIgnoresTheSlabBeingUpdated() {
		FareSlab existing = slab(1L, 0, 5);
		when(fareSlabRepository.findById(1L)).thenReturn(Optional.of(existing));
		when(fareSlabRepository.findAll()).thenReturn(List.of(existing));
		when(fareSlabRepository.save(any(FareSlab.class))).thenReturn(existing);

		FareSlabDto dto = controller.updateFareSlab(1L, request(0, 5));

		assertEquals(1L, dto.getId());
		verify(fareSlabRepository).save(existing);
	}

	private FareSlabCreateDto request(double min, double max) {
		FareSlabCreateDto dto = new FareSlabCreateDto();
		dto.setMinDistanceKm(min);
		dto.setMaxDistanceKm(max);
		dto.setFare(new BigDecimal("10.00"));
		return dto;
	}

	private FareSlab slab(Long id, double min, double max) {
		FareSlab slab = new FareSlab();
		slab.setId(id);
		slab.setMinDistanceKm(min);
		slab.setMaxDistanceKm(max);
		slab.setFare(new BigDecimal("10.00"));
		return slab;
	}
}

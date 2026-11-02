package com.metro.stationservice.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.metro.stationservice.dto.request.SegmentCreateDto;
import com.metro.stationservice.dto.response.SegmentDto;
import com.metro.stationservice.entity.Line;
import com.metro.stationservice.entity.Segment;
import com.metro.stationservice.entity.Station;
import com.metro.stationservice.exception.InvalidSegmentException;
import com.metro.stationservice.exception.LineNotFoundException;
import com.metro.stationservice.exception.SegmentNotFoundException;
import com.metro.stationservice.exception.StationNotFoundException;
import com.metro.stationservice.repository.LineRepository;
import com.metro.stationservice.repository.SegmentRepository;
import com.metro.stationservice.repository.StationRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/segments")
public class SegmentController {

	@Autowired
	public SegmentRepository segmentRepository;

	@Autowired
	public StationRepository stationRepository;

	@Autowired
	public LineRepository lineRepository;

	@GetMapping
	public List<SegmentDto> getAllSegments(){
		List<Segment> segments = segmentRepository.findAll();
		List<SegmentDto> result = new ArrayList<>();
		for (Segment segment : segments) {
			result.add(toDto(segment));
		}
		return result;
	}

	@GetMapping("/{id}")
	public SegmentDto getSegment(@PathVariable Long id) {
		Segment segment = segmentRepository.findById(id).orElse(null);
		if (segment == null) {
			throw new SegmentNotFoundException(id);
		}
		return toDto(segment);
	}

	@PostMapping
	public SegmentDto addSegment(@Valid @RequestBody SegmentCreateDto request) {
		Segment segment = new Segment();
		fillSegment(segment, request);

		Segment saved = segmentRepository.save(segment);
		return toDto(saved);
	}

	@PutMapping("/{id}")
	public SegmentDto updateSegment(@PathVariable Long id, @Valid @RequestBody SegmentCreateDto request) {
		Segment segment = segmentRepository.findById(id).orElse(null);
		if (segment == null) {
			throw new SegmentNotFoundException(id);
		}
		fillSegment(segment, request);

		Segment saved = segmentRepository.save(segment);
		return toDto(saved);
	}

	@DeleteMapping("/{id}")
	public void removeSegment(@PathVariable Long id) {
		if (!segmentRepository.existsById(id)) {
			throw new SegmentNotFoundException(id);
		}
		segmentRepository.deleteById(id);
	}

	// copies the request onto the segment, after checking the stations and line exist
	private void fillSegment(Segment segment, SegmentCreateDto request) {
		if (request.getStationAId().equals(request.getStationBId())) {
			throw new InvalidSegmentException("A segment must connect two different stations");
		}

		Station stationA = stationRepository.findById(request.getStationAId()).orElse(null);
		if (stationA == null) {
			throw new StationNotFoundException(request.getStationAId());
		}
		Station stationB = stationRepository.findById(request.getStationBId()).orElse(null);
		if (stationB == null) {
			throw new StationNotFoundException(request.getStationBId());
		}
		Line line = lineRepository.findById(request.getLineId()).orElse(null);
		if (line == null) {
			throw new LineNotFoundException(request.getLineId());
		}

		segment.setStationA(stationA);
		segment.setStationB(stationB);
		segment.setLine(line);
		segment.setDistanceKm(request.getDistanceKm());
	}

	private SegmentDto toDto(Segment segment) {
		SegmentDto dto = new SegmentDto();
		dto.setId(segment.getId());
		dto.setStationAId(segment.getStationA().getId());
		dto.setStationBId(segment.getStationB().getId());
		dto.setStationAName(segment.getStationA().getName());
		dto.setStationBName(segment.getStationB().getName());
		dto.setLineId(segment.getLine().getId());
		dto.setLineName(segment.getLine().getName());
		dto.setDistanceKm(segment.getDistanceKm());
		return dto;
	}

}

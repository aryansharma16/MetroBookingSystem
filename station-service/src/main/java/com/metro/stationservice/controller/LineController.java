package com.metro.stationservice.controller;

import com.metro.stationservice.dto.response.LineDto;
import com.metro.stationservice.dto.response.StationDto;
import com.metro.stationservice.dto.request.LineCreateDto;
import com.metro.stationservice.entity.Line;
import com.metro.stationservice.entity.Segment;
import com.metro.stationservice.entity.Station;
import com.metro.stationservice.exception.LineInUseException;
import com.metro.stationservice.exception.LineNotFoundException;
import com.metro.stationservice.repository.LineRepository;
import com.metro.stationservice.repository.SegmentRepository;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lines")
public class LineController {

    @Autowired
    private LineRepository lineRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    @GetMapping("/{id}")
    public LineDto getLine(@PathVariable Long id) {
        Line line = lineRepository.findById(id).orElse(null);
        if (line == null) {
            throw new LineNotFoundException(id);
        }
        return toDto(line);
    }

    // distinct stations on this line, in the order the segments are read
    @GetMapping("/{id}/stations")
    public List<StationDto> getLineStations(@PathVariable Long id) {
        Line line = lineRepository.findById(id).orElse(null);
        if (line == null) {
            throw new LineNotFoundException(id);
        }

        List<Segment> segments = segmentRepository.findByLineId(id);
        Map<Long, Station> stations = new LinkedHashMap<>();
        for (Segment segment : segments) {
            stations.putIfAbsent(segment.getStationA().getId(), segment.getStationA());
            stations.putIfAbsent(segment.getStationB().getId(), segment.getStationB());
        }

        List<StationDto> result = new ArrayList<>();
        for (Station station : stations.values()) {
            StationDto dto = new StationDto();
            dto.setId(station.getId());
            dto.setName(station.getName());
            dto.setCode(station.getCode());
            result.add(dto);
        }
        return result;
    }

    @PutMapping("/{id}")
    public LineDto updateLine(@PathVariable Long id, @Valid @RequestBody LineCreateDto request) {
        Line line = lineRepository.findById(id).orElse(null);
        if (line == null) {
            throw new LineNotFoundException(id);
        }
        line.setName(request.getName());
        line.setColor(request.getColor());
        return toDto(lineRepository.save(line));
    }

    @GetMapping
    public List<LineDto> getAllLines() {
        List<Line> lines = lineRepository.findAll();
        List<LineDto> result = new ArrayList<>();

        for (Line line : lines) {
            LineDto dto = new LineDto();
            dto.setId(line.getId());
            dto.setName(line.getName());
            dto.setColor(line.getColor());
            result.add(dto);
        }

        return result;
    }

    @PostMapping
    public LineDto addLine(@Valid @RequestBody LineCreateDto request) {
        Line line = new Line();
        line.setName(request.getName());
        line.setColor(request.getColor());
        Line saved = lineRepository.save(line);

        LineDto dto = new LineDto();
        dto.setId(saved.getId());
        dto.setName(saved.getName());
        dto.setColor(saved.getColor());
        return dto;
    }

    @DeleteMapping("/{id}")
    public void deleteLine(@PathVariable Long id) {
        if (!lineRepository.existsById(id)) {
            throw new LineNotFoundException(id);
        }
        if (segmentRepository.existsByLineId(id)) {
            throw new LineInUseException(id);
        }
        lineRepository.deleteById(id);
    }

    private LineDto toDto(Line line) {
        LineDto dto = new LineDto();
        dto.setId(line.getId());
        dto.setName(line.getName());
        dto.setColor(line.getColor());
        return dto;
    }
}
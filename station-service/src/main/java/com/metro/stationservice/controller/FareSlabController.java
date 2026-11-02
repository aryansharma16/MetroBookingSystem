package com.metro.stationservice.controller;

import com.metro.stationservice.dto.response.FareSlabDto;
import com.metro.stationservice.dto.request.FareSlabCreateDto;
import com.metro.stationservice.entity.FareSlab;
import com.metro.stationservice.exception.FareSlabNotFoundException;
import com.metro.stationservice.exception.InvalidFareSlabException;
import com.metro.stationservice.repository.FareSlabRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/fare-slabs")
public class FareSlabController {

    @Autowired
    private FareSlabRepository fareSlabRepository;

    @GetMapping
    public List<FareSlabDto> getAllFareSlabs() {
        List<FareSlab> slabs = fareSlabRepository.findAll();
        List<FareSlabDto> result = new ArrayList<>();

        for (FareSlab slab : slabs) {
            result.add(toDto(slab));
        }

        return result;
    }

    @GetMapping("/{id}")
    public FareSlabDto getFareSlab(@PathVariable Long id) {
        FareSlab slab = fareSlabRepository.findById(id).orElse(null);
        if (slab == null) {
            throw new FareSlabNotFoundException(id);
        }
        return toDto(slab);
    }

    @PostMapping
    public FareSlabDto addFareSlab(@Valid @RequestBody FareSlabCreateDto request) {
        validateFareSlab(request, null);

        FareSlab slab = new FareSlab();
        slab.setMinDistanceKm(request.getMinDistanceKm());
        slab.setMaxDistanceKm(request.getMaxDistanceKm());
        slab.setFare(request.getFare());
        FareSlab saved = fareSlabRepository.save(slab);

        return toDto(saved);
    }

    @PutMapping("/{id}")
    public FareSlabDto updateFareSlab(@PathVariable Long id, @Valid @RequestBody FareSlabCreateDto request) {
        FareSlab slab = fareSlabRepository.findById(id).orElse(null);
        if (slab == null) {
            throw new FareSlabNotFoundException(id);
        }
        validateFareSlab(request, id);

        slab.setMinDistanceKm(request.getMinDistanceKm());
        slab.setMaxDistanceKm(request.getMaxDistanceKm());
        slab.setFare(request.getFare());
        FareSlab saved = fareSlabRepository.save(slab);

        return toDto(saved);
    }

    @DeleteMapping("/{id}")
    public void deleteFareSlab(@PathVariable Long id) {
        if (!fareSlabRepository.existsById(id)) {
            throw new FareSlabNotFoundException(id);
        }
        fareSlabRepository.deleteById(id);
    }

    // currentId is the slab being updated (null on create), so it is not compared with itself
    private void validateFareSlab(FareSlabCreateDto request, Long currentId) {
        if (request.getMinDistanceKm() >= request.getMaxDistanceKm()) {
            throw new InvalidFareSlabException("Minimum distance must be less than maximum distance");
        }

        // touching edges (0-5 and 5-10) are allowed, RouteService takes the first match
        List<FareSlab> slabs = fareSlabRepository.findAll();
        for (FareSlab other : slabs) {
            if (currentId != null && currentId.equals(other.getId())) {
                continue;
            }
            if (request.getMinDistanceKm() < other.getMaxDistanceKm()
                    && other.getMinDistanceKm() < request.getMaxDistanceKm()) {
                throw new InvalidFareSlabException("Fare slab overlaps with existing slab "
                        + other.getMinDistanceKm() + "-" + other.getMaxDistanceKm() + " km");
            }
        }
    }

    private FareSlabDto toDto(FareSlab slab) {
        FareSlabDto dto = new FareSlabDto();
        dto.setId(slab.getId());
        dto.setMinDistanceKm(slab.getMinDistanceKm());
        dto.setMaxDistanceKm(slab.getMaxDistanceKm());
        dto.setFare(slab.getFare());
        return dto;
    }
}

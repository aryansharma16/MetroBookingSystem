package com.metro.stationservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.metro.stationservice.entity.Segment;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

	boolean existsByStationAIdOrStationBId(Long stationAId, Long stationBId);

	boolean existsByLineId(Long lineId);

	List<Segment> findByLineId(Long lineId);
}

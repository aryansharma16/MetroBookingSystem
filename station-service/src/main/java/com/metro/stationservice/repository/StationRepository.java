package com.metro.stationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.metro.stationservice.entity.Station;

public interface StationRepository extends JpaRepository<Station, Long>{
	boolean existsByNameIgnoreCase(String name);
	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

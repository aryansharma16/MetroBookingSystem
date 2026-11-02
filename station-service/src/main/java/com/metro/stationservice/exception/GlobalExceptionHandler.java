package com.metro.stationservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(StationNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleStationNotFound(StationNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(LineNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleLineNotFound(LineNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FareNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFareNotFound(FareNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SameStationException.class)
    public ResponseEntity<Map<String, String>> handleSameStation(SameStationException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(StationAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleSameStation(StationAlreadyExistsException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(StationInUseException.class)
    public ResponseEntity<Map<String, String>> handleStationInUse(StationInUseException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(LineInUseException.class)
    public ResponseEntity<Map<String, String>> handleLineInUse(LineInUseException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SegmentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSegmentNotFound(SegmentNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidSegmentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSegment(InvalidSegmentException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FareSlabNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFareSlabNotFound(FareSlabNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidFareSlabException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFareSlab(InvalidFareSlabException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException e) {
    	Map<String, String> body = new HashMap<String, String>();
    	body.put("error", "This record conflicts with an existing one.");
    	return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
    
 // NEW: 'to' station does not exist or is not connected to 'from'
    @ExceptionHandler(RouteNotReachableException.class)
    public ResponseEntity<Map<String, String>> handleRouteNotReachable(RouteNotReachableException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }
    
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
    
    private ResponseEntity<Map<String,String>> buildErrorResponse(String message , HttpStatus status){
    	Map<String , String> error = new HashMap<>();
    	error.put("error", message);
    	error.put("status", status.toString());
    	return new ResponseEntity<>(error , status);
    }
    
    
}

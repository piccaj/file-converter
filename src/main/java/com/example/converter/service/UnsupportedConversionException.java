package com.example.converter.service;

/**
 * Thrown when the input can't be decoded or the requested conversion isn't
 * supported — maps to HTTP 400/415 rather than a 500.
 */
public class UnsupportedConversionException extends Exception {

    public UnsupportedConversionException(String message) {
        super(message);
    }
}

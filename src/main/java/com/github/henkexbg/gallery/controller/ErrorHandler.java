package com.github.henkexbg.gallery.controller;

import com.github.henkexbg.gallery.controller.exception.ResourceNotFoundException;
import com.github.henkexbg.gallery.controller.model.GalleryError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GalleryError> handleException(Exception ex) {
        GalleryError error = new GalleryError();
        HttpStatus responseStatus;
        if (ex instanceof ResourceNotFoundException) {
            responseStatus = HttpStatus.NOT_FOUND;
        } else {
            responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        error.setErrorCode(responseStatus.value());
        return new ResponseEntity<>(error, responseStatus);
    }
}
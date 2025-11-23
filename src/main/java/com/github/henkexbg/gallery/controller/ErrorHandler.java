package com.github.henkexbg.gallery.controller;

import com.github.henkexbg.gallery.controller.exception.ResourceNotFoundException;
import com.github.henkexbg.gallery.controller.model.GalleryError;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.FileNotFoundException;

@ControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GalleryError> handleException(Exception ex) {
        GalleryError error = new GalleryError();
        HttpStatus responseStatus;
        String errorMessage = ex.getMessage();
        if (ex instanceof ResourceNotFoundException || ex instanceof FileNotFoundException) {
            responseStatus = HttpStatus.NOT_FOUND;
        } else if (ex instanceof NotAllowedException) {
            responseStatus = HttpStatus.FORBIDDEN;
        } else if (ex instanceof IllegalArgumentException) {
            responseStatus = HttpStatus.BAD_REQUEST;
        } else {
            responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = "Internal Server Error";
        }
        error.setErrorCode(responseStatus.value());
        error.setErrorMessage(errorMessage);
        return new ResponseEntity<>(error, responseStatus);
    }

}
package com.m.whatsapp.router.exceptions;

import java.io.IOException;

/**
 * Created by dmoreira <diegomoreira00@gmail.com> on 15/11/2020.
 */
public class GenericNetworkException extends RuntimeException {

    public GenericNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
